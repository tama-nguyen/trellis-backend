"""The four roles of a worker pipeline, plus the DLQ publisher.

    EventSource --deliveries--> [envelope + payload parse] --> Filter* --> Transformer* --> Action
         ^                                                                                   |
         +------------------------------ ack(delivery, processed) <---------------------------+

Contract (shared with the Java consumer framework):

* ``Filter.accept`` returns False to drop an event. Dropped events are acknowledged.
* ``Transformer.transform`` returns a (possibly new) event, or None to drop it (acknowledged).
* ``Action.act`` returns None or True when done: the event is acknowledged.
  It returns **False** to *defer*: the event stays unacknowledged until the action calls
  ``event.settle()``. On Kafka this holds the partition's committed offset at that event, so later
  events of the partition are processed but their offsets are not committed past it. (DataHub's
  ``ack(processed=False)`` is a no-op while a later success commits a higher offset, which silently
  skips the deferred event; Trellis tracks offsets per partition to avoid that.)
* Raising means "retry": the pipeline retries with backoff. Raise
  :class:`~trellis_core.errors.PermanentError` to skip the retries and go to the DLQ at once.
* ``EventSource.request_stop`` only sets a flag and must be safe to call from a signal handler.
  ``close`` releases resources after ``events()`` has returned; it is idempotent.
"""

from collections.abc import Iterator
from typing import Protocol, runtime_checkable

from trellis_events.pipeline.model import Delivery, DlqRecord, InboundEvent
from trellis_events.pipeline.offsets import PartitionId


@runtime_checkable
class EventSource(Protocol):
    def events(self) -> Iterator[Delivery]:
        """Yields deliveries until the source is drained or :meth:`request_stop` is called."""
        ...

    def ack(self, delivery: Delivery, processed: bool) -> None:
        """``processed=True``: done, the position may advance past it. ``False``: keep it pending."""
        ...

    def request_stop(self) -> None: ...

    def close(self) -> None: ...

    def is_ready(self) -> bool:
        """Connected to the broker (for /health/ready). Cheap: must not do network I/O."""
        ...

    def lag(self) -> dict[PartitionId, int]:
        """Messages not yet acknowledged per assigned partition, as last measured (may be empty)."""
        ...


@runtime_checkable
class Filter(Protocol):
    def accept(self, event: InboundEvent) -> bool: ...


@runtime_checkable
class Transformer(Protocol):
    def transform(self, event: InboundEvent) -> InboundEvent | None: ...


@runtime_checkable
class Action(Protocol):
    def act(self, event: InboundEvent) -> bool | None: ...


class DlqPublishError(Exception):
    """The DLQ did not confirm the write. The event must not be acknowledged."""


@runtime_checkable
class DlqPublisher(Protocol):
    def publish(self, record: DlqRecord) -> None:
        """Returns only once the DLQ has durably accepted the record; raises DlqPublishError."""
        ...

    def close(self) -> None: ...
