"""``ConfigModel``: the base class for every configuration object in Trellis.

What the base gives every subclass:

* ``extra="forbid"``: a mistyped key is an error at startup, not a silently ignored setting.
* ``hide_input_in_errors``: validation errors never echo the offending value (it may be a secret).
  This is unconditional on purpose: a debug switch that leaks secrets ends up enabled in production.
* Every ``SecretStr``/``SecretBytes`` field is registered with :class:`SecretRegistry` on validation,
  so the value is masked in every log line from then on.
* :meth:`ConfigModel.model_dump_redacted` masks by field *type*, not by guessing from key names.

Pattern adapted from DataHub's ``datahub.configuration.common.ConfigModel`` (re-implemented).
"""

from collections.abc import Mapping, Sequence
from enum import Enum
from typing import ClassVar, Self

from pydantic import BaseModel, ConfigDict, SecretBytes, SecretStr, model_validator

from trellis_core.json_types import JsonValue
from trellis_core.secrets import MASK, SecretRegistry


class ConfigModel(BaseModel):
    """Strict, secret-aware pydantic base model for configuration."""

    model_config = ConfigDict(
        extra="forbid",
        hide_input_in_errors=True,
        validate_default=True,
        use_enum_values=False,
    )

    #: Set to False on a subclass to keep its secrets out of the registry (e.g. a test fixture).
    register_secrets: ClassVar[bool] = True

    @model_validator(mode="after")
    def _register_secret_fields(self) -> Self:
        if not self.register_secrets:
            return self
        registry = SecretRegistry.default()
        for path, secret in _iter_secrets(self, prefix=type(self).__name__):
            registry.register(path, secret)
        return self

    def model_dump_redacted(self) -> dict[str, JsonValue]:
        """``model_dump`` for logs and ``config show``: secrets become ``********``."""
        return redacted_dump(self)


def redacted_dump(model: BaseModel) -> dict[str, JsonValue]:
    """Dumps any pydantic model (settings included) with secrets masked by field type."""
    dumped = _redact(model)
    if not isinstance(dumped, dict):  # pragma: no cover - a model always dumps to an object
        raise TypeError("model did not dump to an object")
    return dumped


def _iter_secrets(value: object, prefix: str) -> list[tuple[str, SecretStr | SecretBytes]]:
    found: list[tuple[str, SecretStr | SecretBytes]] = []
    if isinstance(value, SecretStr | SecretBytes):
        found.append((prefix, value))
    elif isinstance(value, BaseModel):
        for name in type(value).model_fields:
            found.extend(_iter_secrets(getattr(value, name), f"{prefix}.{name}"))
    elif isinstance(value, Mapping):
        for key, item in value.items():
            found.extend(_iter_secrets(item, f"{prefix}.{key}"))
    elif isinstance(value, Sequence) and not isinstance(value, str | bytes):
        for index, item in enumerate(value):
            found.extend(_iter_secrets(item, f"{prefix}[{index}]"))
    return found


def _redact(value: object) -> JsonValue:
    if isinstance(value, SecretStr | SecretBytes):
        return MASK
    if isinstance(value, BaseModel):
        return {name: _redact(getattr(value, name)) for name in type(value).model_fields}
    if isinstance(value, Enum):
        return _redact(value.value)
    if value is None or isinstance(value, str | bool | int | float):
        return value
    if isinstance(value, Mapping):
        return {str(key): _redact(item) for key, item in value.items()}
    if isinstance(value, Sequence) and not isinstance(value, bytes):
        return [_redact(item) for item in value]
    # Paths, datetimes, URLs: their string form carries no secret by construction.
    return str(value)
