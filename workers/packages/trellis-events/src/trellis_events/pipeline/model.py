"""Data passed between the pipeline roles. Internal data is dataclasses; wire data is pydantic."""

from collections.abc import Callable, Mapping
from dataclasses import dataclass, field
from datetime import datetime

from pydantic import BaseModel

from trellis_events.constants import DLQ_ERROR_MAX_CHARS, DLQ_SUFFIX, DlqHeaders
from trellis_events.envelope import Envelope


@dataclass(frozen=True)
class Delivery:
    """One record as the transport delivered it, before any parsing."""

    value: bytes
    key: bytes | None
    headers: Mapping[str, bytes]
    topic: str
    partition: int
    offset: int

    @property
    def ordering_key(self) -> str:
        """Key for in-process ordering; equals the broker key, so it matches broker ordering."""
        if self.key:
            return self.key.decode("utf-8", errors="replace")
        return f"{self.topic}/{self.partition}"

    def describe(self) -> str:
        return f"{self.topic}[{self.partition}]@{self.offset}"


def _no_settle() -> None:
    raise RuntimeError("settle() is only available on events whose action returned False")


@dataclass
class InboundEvent:
    """A parsed event flowing through filters, transformers and the action."""

    delivery: Delivery
    envelope: Envelope
    payload: BaseModel
    attempt: int = 1
    #: Set by the pipeline when the action returned False (deferred); call it once the work is done.
    settle: Callable[[], None] = field(default=_no_settle, repr=False)


@dataclass(frozen=True)
class DlqRecord:
    """What the DLQ publisher writes: the original bytes, original headers, plus ``x-dlq-*``."""

    delivery: Delivery
    event_type: str | None
    consumer: str
    error: BaseException
    attempts: int
    failed_at: datetime

    @property
    def topic(self) -> str:
        # The body may be unreadable, in which case the event type is unknown: use the source topic.
        return f"{self.event_type or self.delivery.topic}{DLQ_SUFFIX}"

    def headers(self) -> dict[str, bytes]:
        error_text = f"{type(self.error).__name__}: {self.error}"[:DLQ_ERROR_MAX_CHARS]
        added = {
            DlqHeaders.ORIGINAL_TOPIC: self.delivery.topic,
            DlqHeaders.ORIGINAL_PARTITION: str(self.delivery.partition),
            DlqHeaders.ORIGINAL_OFFSET: str(self.delivery.offset),
            DlqHeaders.CONSUMER: self.consumer,
            DlqHeaders.EXCEPTION: f"{type(self.error).__module__}.{type(self.error).__qualname__}",
            DlqHeaders.ERROR: error_text,
            DlqHeaders.ATTEMPTS: str(self.attempts),
            DlqHeaders.FAILED_AT: self.failed_at.isoformat(),
        }
        return {**self.delivery.headers, **{k: v.encode("utf-8") for k, v in added.items()}}
