"""Validates raw event JSON against the JSON Schemas in ``contracts/events``.

The schemas are the source of truth shared by Java, Python and TypeScript; this module reads them
directly (not the pydantic mirrors), so ``trellis events validate`` answers "does this match the
contract" rather than "does Python accept it". Formats (``uuid``, ``date-time``) are checked: by
default jsonschema treats ``format`` as an annotation and would accept ``"occurredAt": "yesterday"``.
"""

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Final

from jsonschema import Draft202012Validator, FormatChecker

from trellis_core.env_vars import get_settings
from trellis_core.errors import ConfigurationError
from trellis_core.json_types import JsonValue, as_json_value

ENVELOPE_SCHEMA: Final = Path("events") / "envelope.schema.json"
_FORMAT_CHECKER = FormatChecker()


@dataclass(frozen=True)
class ContractIssue:
    location: str  # JSON pointer-ish path, e.g. "payload/noticeCode"
    message: str
    schema: str  # which schema file reported it, relative to contracts/


def find_contracts_dir(start: Path | None = None) -> Path:
    """``TRELLIS_CONTRACTS_DIR``, else the nearest ``contracts/`` above ``start`` (default: cwd)."""
    configured = get_settings().contracts_dir
    if configured is not None:
        if not (configured / ENVELOPE_SCHEMA).is_file():
            raise ConfigurationError(
                f"TRELLIS_CONTRACTS_DIR has no {ENVELOPE_SCHEMA}: {configured}"
            )
        return configured
    here = (start or Path.cwd()).resolve()
    for candidate in (here, *here.parents):
        if (candidate / "contracts" / ENVELOPE_SCHEMA).is_file():
            return candidate / "contracts"
    raise ConfigurationError(
        "cannot find contracts/events/envelope.schema.json above the working directory; "
        "set TRELLIS_CONTRACTS_DIR or pass --contracts-dir"
    )


class ContractValidator:
    def __init__(self, contracts_dir: Path) -> None:
        self._root = contracts_dir
        self._cache: dict[Path, Draft202012Validator] = {}

    def _validator(self, relative: Path) -> Draft202012Validator | None:
        if relative not in self._cache:
            path = self._root / relative
            if not path.is_file():
                return None
            schema = json.loads(path.read_text(encoding="utf-8"))
            Draft202012Validator.check_schema(schema)
            self._cache[relative] = Draft202012Validator(schema, format_checker=_FORMAT_CHECKER)
        return self._cache[relative]

    @staticmethod
    def payload_schema_path(event_type: str, version: int) -> Path:
        return Path("events") / event_type / f"v{version}.schema.json"

    def validate(self, document: JsonValue) -> list[ContractIssue]:
        """Envelope first; the payload only when the envelope is valid (else the type is unknown)."""
        envelope_validator = self._validator(ENVELOPE_SCHEMA)
        if envelope_validator is None:
            raise ConfigurationError(f"missing {self._root / ENVELOPE_SCHEMA}")
        issues = _issues(envelope_validator, document, "", str(ENVELOPE_SCHEMA))
        if issues or not isinstance(document, dict):
            return issues
        event_type, version = document["eventType"], document["eventVersion"]
        if not isinstance(event_type, str) or not isinstance(version, int):
            return issues  # unreachable after a valid envelope; keeps mypy precise
        relative = self.payload_schema_path(event_type, version)
        payload_validator = self._validator(relative)
        if payload_validator is None:
            return [
                ContractIssue(
                    "eventType", f"no contract for {event_type} v{version}", str(relative)
                )
            ]
        return _issues(payload_validator, document["payload"], "payload", str(relative))

    def validate_file(self, path: Path) -> list[ContractIssue]:
        """Reads and validates one JSON document (raises ``OSError``/``ValueError`` on bad input)."""
        return self.validate(as_json_value(json.loads(path.read_text(encoding="utf-8"))))


def _issues(
    validator: Draft202012Validator, instance: JsonValue, prefix: str, schema: str
) -> list[ContractIssue]:
    found = []
    for error in sorted(validator.iter_errors(instance), key=lambda e: list(e.absolute_path)):
        parts = [prefix, *(str(p) for p in error.absolute_path)]
        found.append(ContractIssue("/".join(p for p in parts if p) or "/", error.message, schema))
    return found
