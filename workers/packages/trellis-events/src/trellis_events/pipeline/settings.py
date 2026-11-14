"""Pipeline configuration and its report."""

from dataclasses import dataclass
from enum import StrEnum
from typing import Annotated, Final

from pydantic import BeforeValidator, Field

from trellis_core.backoff import BackoffConfig
from trellis_core.config import ConfigModel
from trellis_core.report import Report

CONSUMER_NAME_PATTERN: Final = r"^[a-z][a-z0-9-]*$"
CONSUMER_NAME_MAX_LENGTH: Final = 100  # processed_event.consumer_name is VARCHAR(100)
MAX_CONCURRENCY: Final = 64


def _upper(value: object) -> object:
    return value.upper() if isinstance(value, str) else value


class FailureMode(StrEnum):
    """What happens after the last retry fails.

    * ``CONTINUE``: publish the event to ``<eventType>.dlq``, then acknowledge it and move on.
      If the DLQ write fails the event is *not* acknowledged and the pipeline stops.
    * ``THROW``: do not acknowledge, do not dead-letter; stop the pipeline (exit 1). The event is
      redelivered after a restart. Use it when later events of the same key must never overtake a
      failed one.
    """

    CONTINUE = "CONTINUE"
    THROW = "THROW"


class PipelineSettings(ConfigModel):
    consumer_name: Annotated[
        str, Field(pattern=CONSUMER_NAME_PATTERN, max_length=CONSUMER_NAME_MAX_LENGTH)
    ]
    """Inbox key and Kafka ``group.id``. Renaming it reprocesses everything: treat it as permanent."""
    failure_mode: Annotated[FailureMode, BeforeValidator(_upper)] = FailureMode.CONTINUE
    retry: BackoffConfig = BackoffConfig()
    concurrency: Annotated[int, Field(ge=1, le=MAX_CONCURRENCY)] = 1
    """Parallel keys. 1 = strictly sequential; >1 keeps per-key order via PartitionExecutor."""
    max_pending: Annotated[int, Field(ge=1)] = 100
    """Events queued or running before the source stops polling (backpressure)."""


@dataclass
class PipelineReport(Report):
    received: int = 0
    processed: int = 0
    duplicates: int = 0
    filtered: int = 0
    dropped: int = 0
    deferred: int = 0
    retries: int = 0
    dead_lettered: int = 0
    failed: int = 0
