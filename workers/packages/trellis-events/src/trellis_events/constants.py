"""Wire-level names shared with the Java side. Change them in both places or not at all."""

from typing import Final


class Headers:
    """Kafka headers. They mirror envelope fields so tools can route without parsing the body."""

    EVENT_ID: Final = "x-event-id"
    EVENT_TYPE: Final = "x-event-type"
    EVENT_VERSION: Final = "x-event-version"
    CORRELATION_ID: Final = "x-correlation-id"
    TRACEPARENT: Final = "traceparent"


class DlqHeaders:
    """Headers added to a dead-lettered record (the original headers and body are kept as is)."""

    ORIGINAL_TOPIC: Final = "x-dlq-original-topic"
    ORIGINAL_PARTITION: Final = "x-dlq-original-partition"
    ORIGINAL_OFFSET: Final = "x-dlq-original-offset"
    CONSUMER: Final = "x-dlq-consumer"
    EXCEPTION: Final = "x-dlq-exception"
    ERROR: Final = "x-dlq-error"
    ATTEMPTS: Final = "x-dlq-attempts"
    FAILED_AT: Final = "x-dlq-failed-at"


#: DLQ topic = ``<eventType>`` + this suffix (or ``<source topic>`` + suffix if the body is unreadable).
DLQ_SUFFIX: Final = ".dlq"
#: DLQ error text is truncated so one huge exception message cannot exceed the broker's limits.
DLQ_ERROR_MAX_CHARS: Final = 2_000
#: Entry point group workers register under.
WORKER_ENTRY_POINT_GROUP: Final = "trellis.workers"
