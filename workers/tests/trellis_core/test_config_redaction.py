"""Secrets never leave the process: redacted dumps, validation errors, log lines, ${VAR} values."""

import io
import json

import pytest
from pydantic import SecretStr, ValidationError

from trellis_core.config import ConfigModel
from trellis_core.env_resolver import EnvResolver
from trellis_core.errors import UnboundVariableError
from trellis_core.logs import configure_logging, get_logger
from trellis_core.secrets import MASK, SecretRegistry

PASSWORD = "s3cr3t-p@ssw0rd-value"


class Database(ConfigModel):
    host: str
    password: SecretStr


class AppConfig(ConfigModel):
    name: str
    database: Database
    tokens: list[SecretStr] = []


def _config() -> AppConfig:
    return AppConfig.model_validate(
        {
            "name": "demo",
            "database": {"host": "db.example.test", "password": PASSWORD},
            "tokens": ["first-token-value"],
        }
    )


def test_redacted_dump_masks_secrets_by_type_at_any_depth() -> None:
    dumped = _config().model_dump_redacted()
    assert dumped == {
        "name": "demo",
        "database": {"host": "db.example.test", "password": MASK},
        "tokens": [MASK],
    }


def test_unknown_keys_are_rejected() -> None:
    with pytest.raises(ValidationError, match="Extra inputs are not permitted"):
        AppConfig.model_validate(
            {"name": "x", "database": {"host": "h", "password": "p"}, "nmae": 1}
        )


def test_validation_errors_do_not_echo_input() -> None:
    with pytest.raises(ValidationError) as caught:
        Database.model_validate({"host": ["not", "a", "string"], "password": PASSWORD})
    assert PASSWORD not in str(caught.value)


def test_secret_fields_are_masked_in_every_log_line() -> None:
    _config()
    stream = io.StringIO()
    configure_logging(level="INFO", stream=stream)
    get_logger("test").info("connecting", dsn=f"postgresql://app:{PASSWORD}@db.example.test/app")
    try:
        raise RuntimeError(f"auth failed for first-token-value and {PASSWORD}")
    except RuntimeError:
        get_logger("test").exception("boom")
    output = stream.getvalue()
    assert PASSWORD not in output
    assert "first-token-value" not in output
    assert [json.loads(line)["event"] for line in output.splitlines()] == ["connecting", "boom"]


def test_masking_fails_closed() -> None:
    registry = SecretRegistry()
    registry.register("x", PASSWORD)
    registry._ordered = (PASSWORD, 42)  # type: ignore[assignment]  # corrupt it on purpose
    assert PASSWORD not in registry.mask_or_withhold(f"value={PASSWORD}")


def test_env_resolver_requires_every_variable_and_registers_values() -> None:
    resolver = EnvResolver({"DB_PASSWORD": PASSWORD})
    resolved = resolver.resolve({"password": "${DB_PASSWORD}", "literal": "$${NOT_A_VAR}"})
    assert resolved == {"password": PASSWORD, "literal": "${NOT_A_VAR}"}
    assert SecretRegistry.default().mask(PASSWORD) == MASK
    with pytest.raises(UnboundVariableError, match="MISSING_VAR"):
        resolver.resolve("${MISSING_VAR}")
