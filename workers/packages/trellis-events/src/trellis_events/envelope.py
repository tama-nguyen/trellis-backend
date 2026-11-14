"""The event envelope: the generated model of ``contracts/events/envelope.schema.json`` + behaviour.

Consumers are *tolerant readers*: unknown envelope or payload fields are ignored when reading (the
contract allows additive changes without a version bump), while everything this model writes
validates against the strict JSON Schema (checked by ``tests/trellis_events/test_envelope_contract``).
"""

import json
from datetime import UTC, datetime

from pydantic import ConfigDict, field_validator

from trellis_core.ids import uuid7
from trellis_core.json_types import JsonObject
from trellis_events.generated.envelope import EventEnvelope


class Envelope(EventEnvelope):
    """Generated ``EventEnvelope`` plus behaviour: wire (de)serialisation, ordering key, factory."""

    model_config = ConfigDict(frozen=True, hide_input_in_errors=True)

    @field_validator("occurred_at")
    @classmethod
    def _to_utc(cls, value: datetime) -> datetime:
        return value.astimezone(UTC)

    @property
    def ordering_key(self) -> str:
        """The key that keeps one aggregate's events in order (partition key on the broker)."""
        return self.partition_key or self.aggregate_id

    def to_json(self) -> JsonObject:
        """Wire form: camelCase, absent optionals omitted, ``occurredAt`` as RFC 3339 UTC (``Z``)."""
        dumped: JsonObject = self.model_dump(mode="json", by_alias=True, exclude_none=True)
        return dumped

    def to_bytes(self) -> bytes:
        return json.dumps(self.to_json(), separators=(",", ":"), ensure_ascii=False).encode("utf-8")

    @classmethod
    def from_bytes(cls, raw: bytes) -> "Envelope":
        return cls.model_validate_json(raw)

    @classmethod
    def new(
        cls,
        *,
        event_type: str,
        event_version: int,
        aggregate_type: str,
        aggregate_id: str,
        producer: str,
        payload: JsonObject,
        correlation_id: str | None = None,
        traceparent: str | None = None,
        occurred_at: datetime | None = None,
    ) -> "Envelope":
        """Builds an envelope with a fresh UUIDv7 id (tests, tools; Python workers do not publish
        domain events directly, they call the backend API)."""
        return cls(
            event_id=uuid7(),
            event_type=event_type,
            event_version=event_version,
            aggregate_type=aggregate_type,
            aggregate_id=aggregate_id,
            occurred_at=occurred_at or datetime.now(UTC),
            producer=producer,
            payload=payload,
            correlation_id=correlation_id,
            traceparent=traceparent,
        )
