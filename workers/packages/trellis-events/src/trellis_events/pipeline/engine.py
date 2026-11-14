"""The pipeline engine: delivery semantics live here and nowhere else.

Per delivery::

    decode envelope ----invalid----------------------------------------------> fail (permanent)
    subscribed type? --no--> ack (filtered)
    for attempt in 1..max_attempts:
        parse payload, filters, transformers, action
        ok -> ack | False -> defer | PermanentError -> fail | other error -> sleep backoff, retry
    retries exhausted -> fail
    fail: CONTINUE -> DLQ publish -> ack     (DLQ error: no ack, stop, exit 1)
          THROW    -> no ack, stop, exit 1

Acknowledgement always comes last: an event is acknowledged only after the action succeeded or the
DLQ confirmed the write, so a crash at any point leads to redelivery (at-least-once), and the
inbox makes redelivery harmless.
"""

import functools
import threading
from collections.abc import Sequence
from concurrent.futures import Future
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Final

from opentelemetry.trace import SpanKind, Status, StatusCode
from pydantic import ValidationError

from trellis_core.backoff import Backoff
from trellis_core.closeable import CloseOnce
from trellis_core.errors import ExitCode, PermanentError
from trellis_core.logs import LogKeys, bound_log_context, get_logger
from trellis_core.tracing import TraceParent, context_from_traceparent, get_tracer
from trellis_events.envelope import Envelope
from trellis_events.payloads.registry import PayloadRegistry
from trellis_events.pipeline.model import Delivery, DlqRecord, InboundEvent
from trellis_events.pipeline.partition_executor import PartitionExecutor
from trellis_events.pipeline.roles import Action, DlqPublisher, EventSource, Filter, Transformer
from trellis_events.pipeline.settings import FailureMode, PipelineReport, PipelineSettings
from trellis_events.pipeline.shutdown import ShutdownSignal

_log = get_logger(__name__)
_tracer = get_tracer(__name__)
SPAN_NAME: Final = "trellis.event.process"


class InvalidEnvelopeError(PermanentError):
    """The record body is not a valid envelope."""


@dataclass(frozen=True)
class _Failure:
    error: BaseException
    attempts: int


class Pipeline:
    def __init__(
        self,
        *,
        settings: PipelineSettings,
        source: EventSource,
        action: Action,
        event_types: frozenset[str],
        payloads: PayloadRegistry,
        dlq: DlqPublisher | None,
        filters: Sequence[Filter] = (),
        transformers: Sequence[Transformer] = (),
        shutdown: ShutdownSignal | None = None,
        report: PipelineReport | None = None,
        backoff: Backoff | None = None,
    ) -> None:
        if settings.failure_mode == FailureMode.CONTINUE and dlq is None:
            raise ValueError("failure_mode CONTINUE needs a DLQ publisher")
        self.settings = settings
        self.report = report or PipelineReport()
        self.source = source
        self._action = action
        self._event_types = event_types
        self._payloads = payloads
        self._dlq = dlq
        self._filters = tuple(filters)
        self._transformers = tuple(transformers)
        self._backoff = backoff or Backoff.from_config(settings.retry)
        self._stop = threading.Event()
        self._fatal = threading.Event()
        self._closer = CloseOnce()
        self._closer.add("source", source.close)
        if dlq is not None:
            self._closer.add("dlq", dlq.close)
        self._executor = (
            PartitionExecutor(settings.concurrency, settings.max_pending)
            if settings.concurrency > 1
            else None
        )
        if shutdown is not None:
            shutdown.on_stop(self.request_stop)

    # -- lifecycle ------------------------------------------------------------------------------

    @property
    def stopping(self) -> bool:
        return self._stop.is_set()

    def request_stop(self) -> None:
        """Flag-only, signal-safe. ``run`` returns after the in-flight events are settled."""
        self._stop.set()
        self.source.request_stop()

    def close(self) -> list[tuple[str, BaseException]]:
        """Idempotent; closes every resource even if one fails, and returns the failures."""
        if self._executor is not None:
            self._executor.shutdown(wait=True)
        return self._closer.run()

    def run(self) -> ExitCode:
        """Consumes until the source is drained or stop is requested; then closes everything."""
        _log.info("pipeline started", consumer=self.settings.consumer_name)
        try:
            for delivery in self.source.events():
                if self._executor is None:
                    self._process(delivery)
                else:
                    future = self._executor.submit(
                        delivery.ordering_key, functools.partial(self._process, delivery)
                    )
                    future.add_done_callback(self._on_task_done)
                if self._fatal.is_set() or self._stop.is_set():
                    break
        except Exception:  # noqa: BLE001 - a broken source must still close and exit non-zero
            _log.exception("pipeline aborted by an unexpected error")
            self._fatal.set()
        finally:
            close_errors = self.close()
        exit_code = ExitCode.FAILURE if self._fatal.is_set() or close_errors else ExitCode.OK
        _log.info("pipeline stopped", exit_code=int(exit_code), **_counters(self.report))
        return exit_code

    def _on_task_done(self, future: "Future[None]") -> None:
        error = future.exception()
        if error is not None:
            _log.error("event task crashed", exc_info=error)
            self._fatal.set()
            self.request_stop()

    # -- per event ------------------------------------------------------------------------------

    def _process(self, delivery: Delivery) -> None:
        if self._fatal.is_set():
            # Queued behind a fatal failure (THROW, DLQ down): leave it unacknowledged so no later
            # event of the same key overtakes the failed one.
            return
        self.report.increment("received")
        delivery_context: dict[str, str | int | None] = {
            LogKeys.CONSUMER: self.settings.consumer_name,
            LogKeys.TOPIC: delivery.topic,
            LogKeys.PARTITION: delivery.partition,
            LogKeys.OFFSET: delivery.offset,
        }
        with bound_log_context(**delivery_context):
            try:
                envelope = Envelope.from_bytes(delivery.value)
            except ValidationError as exc:
                error = InvalidEnvelopeError(f"invalid envelope: {exc.error_count()} error(s)")
                self._fail(delivery, None, _Failure(error, 1))
                return
            parent = TraceParent.parse(envelope.traceparent)
            event_context: dict[str, str | int | None] = {
                LogKeys.EVENT_ID: str(envelope.event_id),
                LogKeys.EVENT_TYPE: envelope.event_type,
                LogKeys.CORRELATION_ID: envelope.correlation_id,
                # Producer's trace id even when local tracing is off, so logs still join up.
                LogKeys.TRACE_ID: parent.trace_id if parent else None,
            }
            with (
                bound_log_context(**event_context),
                _tracer.start_as_current_span(
                    SPAN_NAME,
                    context=context_from_traceparent(envelope.traceparent),
                    kind=SpanKind.CONSUMER,
                    attributes={
                        "messaging.destination.name": delivery.topic,
                        "messaging.message.id": str(envelope.event_id),
                        "messaging.consumer.group.name": self.settings.consumer_name,
                    },
                ) as span,
            ):
                if envelope.event_type not in self._event_types:
                    self.report.increment("filtered")
                    self.source.ack(delivery, processed=True)
                    return
                outcome = self._attempt_all(delivery, envelope)
                if isinstance(outcome, _Failure):
                    span.set_status(Status(StatusCode.ERROR, type(outcome.error).__name__))
                    self._fail(delivery, envelope.event_type, outcome)

    def _attempt_all(self, delivery: Delivery, envelope: Envelope) -> _Failure | None:
        attempt = 0
        while True:
            attempt += 1
            try:
                with bound_log_context(**{LogKeys.ATTEMPT: attempt}):
                    self._attempt_once(delivery, envelope, attempt)
                return None
            except PermanentError as exc:
                return _Failure(exc, attempt)
            except Exception as exc:  # noqa: BLE001 - unknown errors are treated as transient
                if not self._backoff.should_retry(attempt):
                    return _Failure(exc, attempt)
                delay_ms = self._backoff.delay_ms(attempt - 1)
                self.report.increment("retries")
                _log.warning(
                    "attempt failed, will retry", attempt=attempt, delay_ms=delay_ms, exc_info=exc
                )
                if self._stop.wait(delay_ms / 1000):
                    _log.info("stop requested during backoff; event left unacknowledged")
                    return None

    def _attempt_once(self, delivery: Delivery, envelope: Envelope, attempt: int) -> None:
        payload = self._payloads.parse(envelope)
        event: InboundEvent | None = InboundEvent(delivery, envelope, payload, attempt)
        for filter_ in self._filters:
            if event is None or not filter_.accept(event):
                self.report.increment("filtered")
                self.source.ack(delivery, processed=True)
                return
        for transformer in self._transformers:
            if event is None:
                break
            event = transformer.transform(event)
        if event is None:
            self.report.increment("dropped")
            self.source.ack(delivery, processed=True)
            return
        event.settle = lambda: self.source.ack(delivery, processed=True)
        result = self._action.act(event)
        if result is False:
            self.report.increment("deferred")
            self.source.ack(delivery, processed=False)
            return
        self.report.increment("processed")
        self.source.ack(delivery, processed=True)

    def _fail(self, delivery: Delivery, event_type: str | None, failure: _Failure) -> None:
        self.report.increment("failed")
        self.report.failure(
            "event failed", context=f"{delivery.describe()} {type(failure.error).__name__}"
        )
        if self.settings.failure_mode == FailureMode.THROW or self._dlq is None:
            _log.error("failure mode THROW: stopping without acknowledging the event")
            self._fatal.set()
            self.request_stop()
            return
        record = DlqRecord(
            delivery=delivery,
            event_type=event_type,
            consumer=self.settings.consumer_name,
            error=failure.error,
            attempts=failure.attempts,
            failed_at=datetime.now(UTC),
        )
        try:
            self._dlq.publish(record)
        except Exception:  # noqa: BLE001 - whatever the cause, the event must stay unacknowledged
            # Acknowledging now would lose the event: stop and let the restart redeliver it.
            _log.exception("DLQ publish failed; event not acknowledged, stopping", dlq=record.topic)
            self._fatal.set()
            self.request_stop()
            return
        self.report.increment("dead_lettered")
        _log.warning("event dead-lettered", dlq=record.topic, attempts=failure.attempts)
        self.source.ack(delivery, processed=True)


def _counters(report: PipelineReport) -> dict[str, int]:
    obj = report.as_obj()
    return {k: v for k, v in obj.items() if isinstance(v, int) and not isinstance(v, bool)}
