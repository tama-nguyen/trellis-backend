"""The Python models must produce exactly what the shared JSON Schemas accept."""

import json
from datetime import UTC, datetime
from pathlib import Path

import time_machine
from jsonschema import Draft202012Validator, FormatChecker

from tests.support import CONTRACTS_DIR, EXAMPLE_ENVELOPE, INVALID_ENVELOPE
from trellis_core.json_types import JsonObject
from trellis_events.contract_validation import ContractValidator
from trellis_events.envelope import Envelope
from trellis_events.generated import notice_published_v1
from trellis_events.generated.notice_published_v1 import NoticePublishedV1
from trellis_events.payloads.registry import default_payload_registry

ENVELOPE_SCHEMA = CONTRACTS_DIR / "events" / "envelope.schema.json"
NOTICE_SCHEMA = CONTRACTS_DIR / "events" / "notice.published" / "v1.schema.json"
FROZEN = datetime(2026, 11, 14, 10, 0, tzinfo=UTC)


def _schema(path: Path) -> JsonObject:
    loaded: JsonObject = json.loads(path.read_text(encoding="utf-8"))
    return loaded


def _strict(schema: JsonObject) -> Draft202012Validator:
    return Draft202012Validator(schema, format_checker=FormatChecker())


def test_committed_example_is_valid() -> None:
    assert ContractValidator(CONTRACTS_DIR).validate_file(EXAMPLE_ENVELOPE) == []


def test_round_trip_of_the_example_stays_valid() -> None:
    envelope = Envelope.from_bytes(EXAMPLE_ENVELOPE.read_bytes())
    payload = default_payload_registry().parse(envelope)
    assert isinstance(payload, NoticePublishedV1)
    assert ContractValidator(CONTRACTS_DIR).validate(envelope.to_json()) == []
    dumped_payload = payload.model_dump(mode="json", by_alias=True, exclude_none=True)
    assert list(_strict(_schema(NOTICE_SCHEMA)).iter_errors(dumped_payload)) == []


@time_machine.travel(FROZEN, tick=False)
def test_built_envelope_is_valid_and_utc() -> None:
    payload = NoticePublishedV1(
        notice_code="NT-7K2M-9QXA-4P8R",
        title="t",
        published_at=FROZEN,
        published_by="admin@example.test",
    )
    envelope = Envelope.new(
        event_type=notice_published_v1.EVENT_TYPE,
        event_version=notice_published_v1.EVENT_VERSION,
        aggregate_type="Notice",
        aggregate_id=payload.notice_code,
        producer="trellis-tests",
        payload=payload.model_dump(mode="json", by_alias=True),
    )
    wire = envelope.to_json()
    assert wire["occurredAt"] == "2026-11-14T10:00:00Z"
    assert envelope.event_id.version == 7
    assert list(_strict(_schema(ENVELOPE_SCHEMA)).iter_errors(wire)) == []


def test_payload_model_declares_the_schema_fields() -> None:
    schema = _schema(NOTICE_SCHEMA)
    properties = schema["properties"]
    required = schema["required"]
    assert isinstance(properties, dict)
    assert isinstance(required, list)
    aliases = {f.alias or name for name, f in NoticePublishedV1.model_fields.items()}
    required_aliases = {
        f.alias or name for name, f in NoticePublishedV1.model_fields.items() if f.is_required()
    }
    assert aliases == set(properties)
    assert required_aliases == set(required)


def test_invalid_fixture_is_rejected_with_locations() -> None:
    issues = ContractValidator(CONTRACTS_DIR).validate_file(INVALID_ENVELOPE)
    assert {i.location for i in issues} == {"eventId", "occurredAt"}
