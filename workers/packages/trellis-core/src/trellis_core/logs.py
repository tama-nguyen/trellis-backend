"""Structured logging: one JSON object per line, with trace and request context, secrets masked.

* ``structlog`` renders every line, including stdlib ``logging`` records from libraries (bridged
  through ``ProcessorFormatter``), so there is one format and one masking point.
* Context comes from ``contextvars`` (:func:`bound_log_context`): the pipeline binds ``event_id``,
  ``correlation_id``, ``trace_id`` ... per event; it follows threads started with
  ``contextvars.copy_context()`` and asyncio tasks, unlike thread-locals.
* ``trace_id``/``span_id`` of the active OpenTelemetry span are added to every line.
* The renderer output passes through :class:`~trellis_core.secrets.SecretRegistry` last, so a secret
  cannot escape through an exception message or a library log either. Masking fails closed.
* Logs go to **stderr**; stdout belongs to command output (``--format json``).

Level policy: ERROR = a human must act; WARNING = unusual but handled; INFO = lifecycle events;
DEBUG = off in production.
"""

import logging
import sys
from collections.abc import Iterator, Mapping, MutableMapping
from contextlib import contextmanager
from typing import Final, TextIO

import structlog
from opentelemetry import trace
from structlog.typing import EventDict, Processor, WrappedLogger

from trellis_core.env_vars import LogFormat, get_settings
from trellis_core.secrets import SecretRegistry


class LogKeys:
    """Context keys shared with the Java MDC names (snake_case in JSON on both sides)."""

    TRACE_ID: Final = "trace_id"
    SPAN_ID: Final = "span_id"
    REQUEST_ID: Final = "request_id"
    CORRELATION_ID: Final = "correlation_id"
    ACTOR: Final = "actor"
    SERVICE: Final = "service"
    ENV: Final = "env"
    CONSUMER: Final = "consumer"
    EVENT_ID: Final = "event_id"
    EVENT_TYPE: Final = "event_type"
    TOPIC: Final = "topic"
    PARTITION: Final = "partition"
    OFFSET: Final = "offset"
    ATTEMPT: Final = "attempt"


def _add_otel_ids(_: WrappedLogger, __: str, event_dict: EventDict) -> EventDict:
    span_context = trace.get_current_span().get_span_context()
    if span_context.is_valid:
        event_dict[LogKeys.TRACE_ID] = format(span_context.trace_id, "032x")
        event_dict[LogKeys.SPAN_ID] = format(span_context.span_id, "016x")
    return event_dict


class _MaskingRenderer:
    """Wraps the final renderer; the rendered line is masked as a whole (fail closed)."""

    def __init__(self, inner: Processor, registry: SecretRegistry) -> None:
        self._inner = inner
        self._registry = registry

    def __call__(
        self, logger: WrappedLogger, name: str, event_dict: EventDict
    ) -> str | bytes | bytearray | Mapping[str, object] | tuple[object, ...]:
        rendered = self._inner(logger, name, event_dict)
        if isinstance(rendered, str):
            return self._registry.mask_or_withhold(rendered)
        # A renderer that does not produce text cannot be masked safely: withhold it.
        return self._registry.mask_or_withhold(str(rendered))


def configure_logging(
    *,
    level: str | None = None,
    fmt: LogFormat | None = None,
    stream: TextIO | None = None,
) -> None:
    """Configures structlog and the stdlib root logger. Call once at process start."""
    settings = get_settings()
    registry = SecretRegistry.default()
    registry.enabled = settings.masking_enabled
    chosen_format = fmt or settings.log_format
    chosen_level = (level or settings.log_level).upper()

    shared: list[Processor] = [
        structlog.contextvars.merge_contextvars,
        structlog.stdlib.add_log_level,
        structlog.stdlib.add_logger_name,
        structlog.processors.TimeStamper(fmt="iso", utc=True, key="ts"),
        _add_otel_ids,
        structlog.processors.StackInfoRenderer(),
        structlog.processors.format_exc_info,
    ]
    inner: Processor = (
        structlog.processors.JSONRenderer(sort_keys=False)
        if chosen_format == LogFormat.JSON
        else structlog.dev.ConsoleRenderer(colors=False)
    )
    structlog.configure(
        processors=[*shared, structlog.stdlib.ProcessorFormatter.wrap_for_formatter],
        logger_factory=structlog.stdlib.LoggerFactory(),
        wrapper_class=structlog.stdlib.BoundLogger,
        cache_logger_on_first_use=True,
    )
    formatter = structlog.stdlib.ProcessorFormatter(
        foreign_pre_chain=shared,
        processors=[
            structlog.stdlib.ProcessorFormatter.remove_processors_meta,
            _MaskingRenderer(inner, registry),
        ],
    )
    handler = logging.StreamHandler(stream if stream is not None else sys.stderr)
    handler.setFormatter(formatter)
    root = logging.getLogger()
    root.handlers.clear()
    root.addHandler(handler)
    root.setLevel(chosen_level)
    structlog.contextvars.clear_contextvars()
    structlog.contextvars.bind_contextvars(
        **{LogKeys.SERVICE: settings.service_name, LogKeys.ENV: settings.env}
    )


def get_logger(name: str) -> structlog.stdlib.BoundLogger:
    logger: structlog.stdlib.BoundLogger = structlog.stdlib.get_logger(name)
    return logger


@contextmanager
def bound_log_context(**values: str | int | None) -> Iterator[None]:
    """Binds keys for the duration of the block (``None`` values are skipped)."""
    clean: MutableMapping[str, str | int] = {k: v for k, v in values.items() if v is not None}
    with structlog.contextvars.bound_contextvars(**clean):
        yield
