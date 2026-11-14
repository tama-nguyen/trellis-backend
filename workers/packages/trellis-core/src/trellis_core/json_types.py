"""JSON value types. Use these instead of ``Any`` for untyped JSON (mypy runs with no explicit Any)."""

from collections.abc import Mapping, Sequence

type JsonScalar = str | int | float | bool | None
type JsonValue = JsonScalar | list[JsonValue] | dict[str, JsonValue]
type JsonObject = dict[str, JsonValue]


def as_json_value(value: object) -> JsonValue:
    """Checks that ``value`` (for example from ``json.load`` or ``tomllib``) is plain JSON.

    Raises ``TypeError`` on anything else, so a typed JsonValue never hides a datetime or an object.
    """
    if value is None or isinstance(value, str | bool | int | float):
        return value
    if isinstance(value, Mapping):
        out: dict[str, JsonValue] = {}
        for key, item in value.items():
            if not isinstance(key, str):
                raise TypeError(f"JSON object keys must be strings, got {type(key).__name__}")
            out[key] = as_json_value(item)
        return out
    if isinstance(value, Sequence) and not isinstance(value, bytes | bytearray):
        return [as_json_value(item) for item in value]
    raise TypeError(f"not a JSON value: {type(value).__name__}")


def as_json_object(value: object) -> JsonObject:
    """Like :func:`as_json_value` but the top level must be an object."""
    checked = as_json_value(value)
    if not isinstance(checked, dict):
        raise TypeError("expected a JSON object at the top level")
    return checked
