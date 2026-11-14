"""Kafka transport (confluent-kafka): the consumer source and the DLQ publisher.

Offset rules (the part DataHub's ``datahub-actions`` gets wrong):

* ``enable.auto.commit=false``. Offsets are committed by this source, synchronously, from the poll
  thread, every ``commit_interval_ms`` and on shutdown/rebalance.
* The committed position of a partition is the **lowest offset still in flight**
  (:class:`~trellis_events.pipeline.offsets.OffsetTracker`), never "the last offset acked".
  An event acked with ``processed=False`` (deferred), a failed event awaiting retry, or an event of
  a slow key keeps the position where it is, even when later offsets of that partition succeed.
  ``datahub-actions`` stores ``offset + 1`` of whichever message was acked last, so one later success
  silently skips a deferred or failed event.
* Revoked partitions are committed before they are given away; lost partitions are only forgotten.
* The DLQ publisher waits for the broker's acknowledgement (``acks=all``, idempotent producer) and
  raises on failure; the pipeline acknowledges the source event only after that.
"""

import logging
import threading
import time
from collections.abc import Iterator
from typing import Annotated, Final, Literal

from confluent_kafka import Consumer, KafkaError, KafkaException, Message, Producer, TopicPartition
from pydantic import Field, SecretStr, field_validator

from trellis_core.config import ConfigModel
from trellis_core.logs import get_logger
from trellis_events.pipeline.model import Delivery, DlqRecord
from trellis_events.pipeline.offsets import OffsetTracker, PartitionId
from trellis_events.pipeline.roles import DlqPublishError

_log = get_logger(__name__)
_SECRET_KEY_HINTS: Final = ("password", "secret", "token", "key")

type ClientConf = dict[str, str | int | float | bool | logging.Logger]
# librdkafka logs through this stdlib logger, so its lines are JSON and secret-masked like ours.
_LIBRDKAFKA_LOGGER = logging.getLogger("librdkafka")


class KafkaTransportConfig(ConfigModel):
    type: Literal["kafka"] = "kafka"
    bootstrap_servers: Annotated[str, Field(min_length=1)]
    topics: list[str] | None = None
    """Default: the worker's event types (topic name = event type)."""
    group_id: str | None = None
    """Default: the pipeline's consumer name."""
    auto_offset_reset: Literal["earliest", "latest"] = "earliest"
    """``earliest``: a new consumer group starts from the beginning; the inbox absorbs replays."""
    poll_timeout_ms: Annotated[int, Field(ge=10)] = 1_000
    commit_interval_ms: Annotated[int, Field(ge=0)] = 1_000
    max_poll_interval_ms: Annotated[int, Field(ge=10_000)] = 300_000
    """Must exceed the slowest event including all retries (default backoff: < 2 min)."""
    session_timeout_ms: Annotated[int, Field(ge=6_000)] = 45_000
    security_protocol: Literal["PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL"] = "PLAINTEXT"
    sasl_mechanism: str | None = None
    sasl_username: str | None = None
    sasl_password: SecretStr | None = None
    dlq_timeout_ms: Annotated[int, Field(ge=100)] = 10_000
    health_refresh_ms: Annotated[int, Field(ge=500)] = 5_000
    """How often the poll thread re-checks broker connectivity and partition lag."""
    client_properties: dict[str, str | int | float | bool] = {}
    """Extra librdkafka properties. Secrets are rejected here: use the ``SecretStr`` fields."""

    @field_validator("client_properties")
    @classmethod
    def _no_secrets(
        cls, value: dict[str, str | int | float | bool]
    ) -> dict[str, str | int | float | bool]:
        leaked = [k for k in value if any(h in k.lower() for h in _SECRET_KEY_HINTS)]
        if leaked:
            raise ValueError(f"secret-looking keys {leaked} belong in a SecretStr field")
        return value


def _common_conf(config: KafkaTransportConfig) -> ClientConf:
    conf: ClientConf = {
        "bootstrap.servers": config.bootstrap_servers,
        "security.protocol": config.security_protocol,
        "logger": _LIBRDKAFKA_LOGGER,
        **config.client_properties,
    }
    if config.sasl_mechanism:
        conf["sasl.mechanism"] = config.sasl_mechanism
    if config.sasl_username:
        conf["sasl.username"] = config.sasl_username
    if config.sasl_password is not None:
        conf["sasl.password"] = config.sasl_password.get_secret_value()
    return conf


def _headers(message: Message) -> dict[str, bytes]:
    out: dict[str, bytes] = {}
    raw = message.headers() or []
    pairs = list(raw.items()) if isinstance(raw, dict) else raw
    for name, value in pairs:
        if isinstance(value, bytes):
            out[name] = value
        elif isinstance(value, str):
            out[name] = value.encode()
    return out


class KafkaSource:
    def __init__(self, config: KafkaTransportConfig, *, group_id: str, topics: list[str]) -> None:
        conf: ClientConf = {
            **_common_conf(config),
            "group.id": group_id,
            "enable.auto.commit": False,
            "enable.auto.offset.store": False,
            "auto.offset.reset": config.auto_offset_reset,
            "max.poll.interval.ms": config.max_poll_interval_ms,
            "session.timeout.ms": config.session_timeout_ms,
            "enable.partition.eof": False,
        }
        self._consumer = Consumer(dict(conf))
        self._topics = topics
        self._poll_timeout = config.poll_timeout_ms / 1000
        self._commit_interval = config.commit_interval_ms / 1000
        self._tracker = OffsetTracker()
        self._stop = threading.Event()
        self._closed = False
        self._last_commit = time.monotonic()
        self._health_interval = config.health_refresh_ms / 1000
        self._last_health = 0.0
        self._health_lock = threading.Lock()
        self._connected = False
        self._lag: dict[PartitionId, int] = {}
        # Heartbeat: a loop that has not polled for max.poll.interval.ms has already been kicked
        # out of the group by the broker, so it is not ready whatever else looks fine.
        self._heartbeat_timeout = config.max_poll_interval_ms / 1000
        self._last_poll: float | None = None

    def events(self) -> Iterator[Delivery]:
        # subscribe() runs here, not in __init__, and close() never runs concurrently with it:
        # closing a consumer that is mid-subscribe races inside librdkafka.
        self._consumer.subscribe(self._topics, on_revoke=self._on_revoke, on_lost=self._on_lost)
        _log.info("kafka consumer subscribed", topics=self._topics)
        while not self._stop.is_set():
            message = self._consumer.poll(self._poll_timeout)
            self._last_poll = time.monotonic()
            self._commit_due()
            self._refresh_health_due()
            if message is None:
                continue
            error = message.error()
            if error is not None:
                if error.code() == KafkaError._PARTITION_EOF:
                    continue
                if error.retriable():
                    _log.warning("retriable kafka error", error=error.str())
                    continue
                raise KafkaException(error)
            delivery = self._to_delivery(message)
            self._tracker.started((delivery.topic, delivery.partition), delivery.offset)
            yield delivery

    @staticmethod
    def _to_delivery(message: Message) -> Delivery:
        topic, partition, offset = message.topic(), message.partition(), message.offset()
        if topic is None or partition is None or offset is None:
            raise KafkaException("message without topic/partition/offset")
        return Delivery(
            value=message.value() or b"",
            key=message.key(),
            headers=_headers(message),
            topic=topic,
            partition=partition,
            offset=offset,
        )

    # -- health: measured on the poll thread (the consumer is not shared), read by the probe thread --

    def _refresh_health_due(self) -> None:
        if time.monotonic() - self._last_health < self._health_interval:
            return
        self._last_health = time.monotonic()
        timeout = min(2.0, self._health_interval)
        try:
            self._consumer.list_topics(timeout=timeout)
            connected = True
        except KafkaException:
            connected = False
        lag: dict[PartitionId, int] = {}
        if connected:
            for tp in self._consumer.assignment():
                try:
                    low, high = self._consumer.get_watermark_offsets(tp, timeout=timeout)
                except KafkaException:
                    continue
                position = self._position(tp, low, timeout)
                lag[(tp.topic, tp.partition)] = max(0, high - position)
        with self._health_lock:
            self._connected = connected
            self._lag = lag

    def _position(self, tp: TopicPartition, low: int, timeout: float) -> int:
        """Where this consumer stands: our in-flight watermark, else the fetch position, else the
        group's committed offset (an idle consumer has fetched nothing yet), else the log start."""
        tracked = self._tracker.position((tp.topic, tp.partition))
        if tracked is not None:
            return tracked
        for candidate in (
            self._consumer.position([tp])[0].offset,
            self._consumer.committed([tp], timeout=timeout)[0].offset,
        ):
            if candidate >= 0:
                return candidate
        return low

    def is_ready(self) -> bool:
        last_poll = self._last_poll
        fresh = last_poll is not None and time.monotonic() - last_poll < self._heartbeat_timeout
        with self._health_lock:
            return fresh and self._connected and not self._stop.is_set()

    def lag(self) -> dict[PartitionId, int]:
        with self._health_lock:
            return dict(self._lag)

    def ack(self, delivery: Delivery, processed: bool) -> None:
        if processed:
            self._tracker.done((delivery.topic, delivery.partition), delivery.offset)

    def _commit_due(self) -> None:
        if time.monotonic() - self._last_commit >= self._commit_interval:
            self._commit(None)

    def _commit(self, only: list[PartitionId] | None) -> None:
        positions = self._tracker.pending_commits()
        if only is not None:
            positions = {tp: pos for tp, pos in positions.items() if tp in only}
        self._last_commit = time.monotonic()
        if not positions:
            return
        offsets = [TopicPartition(t, p, pos) for (t, p), pos in positions.items()]
        self._consumer.commit(offsets=offsets, asynchronous=False)
        self._tracker.mark_committed(positions)
        _log.debug(
            "offsets committed", positions={f"{t}[{p}]": o for (t, p), o in positions.items()}
        )

    def _on_revoke(self, _consumer: Consumer, partitions: list[TopicPartition]) -> None:
        revoked = [(tp.topic, tp.partition) for tp in partitions]
        try:
            self._commit(revoked)
        except KafkaException:
            _log.exception(
                "commit on revoke failed; the new owner will reprocess from the last commit"
            )
        self._tracker.forget(revoked)

    def _on_lost(self, _consumer: Consumer, partitions: list[TopicPartition]) -> None:
        self._tracker.forget([(tp.topic, tp.partition) for tp in partitions])

    def request_stop(self) -> None:
        self._stop.set()

    def close(self) -> None:
        if self._closed:
            return
        try:
            self._commit(None)
        finally:
            self._consumer.close()
            self._closed = True


class KafkaDlqPublisher:
    def __init__(self, config: KafkaTransportConfig) -> None:
        conf: ClientConf = {
            **_common_conf(config),
            "enable.idempotence": True,
            "acks": "all",
        }
        self._producer = Producer(dict(conf))
        self._timeout = config.dlq_timeout_ms / 1000

    def publish(self, record: DlqRecord) -> None:
        outcome: list[KafkaError | None] = []

        def on_delivery(error: KafkaError | None, _message: Message) -> None:
            outcome.append(error)

        try:
            self._producer.produce(
                record.topic,
                value=record.delivery.value,
                key=record.delivery.key,
                headers=list(record.headers().items()),
                on_delivery=on_delivery,
            )
        except (BufferError, KafkaException) as exc:
            raise DlqPublishError(f"cannot enqueue to {record.topic}: {exc}") from exc
        if self._producer.flush(self._timeout) > 0 or not outcome:
            raise DlqPublishError(f"no acknowledgement from {record.topic} within {self._timeout}s")
        if outcome[0] is not None:
            raise DlqPublishError(f"{record.topic} rejected the record: {outcome[0].str()}")

    def close(self) -> None:
        self._producer.flush(self._timeout)
