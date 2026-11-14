"""``${VAR}`` expansion for config files, with ``nounset`` semantics.

* Only the braced form ``${NAME}`` is recognised; ``$NAME`` is left alone (too easy to hit by accident
  in passwords and regexes). ``$${`` is an escape for a literal ``${``.
* An unset variable is an error (:class:`UnboundVariableError`), never an empty string.
* Every expanded value is registered as a secret: config files reference variables precisely for the
  values that must not be committed, so treating all of them as secret is the safe default.
* Values are resolved from a mapping passed in, and never written back to ``os.environ``.

Adapted from DataHub's ``EnvResolver`` (``datahub.configuration.config_loader``), re-implemented.
"""

import json
import re
import tomllib
from collections.abc import Mapping
from pathlib import Path

from trellis_core.errors import ConfigurationError, UnboundVariableError
from trellis_core.json_types import JsonObject, JsonValue, as_json_object
from trellis_core.secrets import SecretRegistry

_REFERENCE = re.compile(r"\$\$\{|\$\{([A-Za-z_][A-Za-z0-9_]*)\}")
_ESCAPED = "$${"


class EnvResolver:
    """Expands ``${VAR}`` in every string of a JSON-like tree."""

    def __init__(self, environ: Mapping[str, str], registry: SecretRegistry | None = None) -> None:
        self._environ = environ
        self._registry = registry or SecretRegistry.default()

    def resolve(self, value: JsonValue) -> JsonValue:
        if isinstance(value, str):
            return self._resolve_str(value)
        if isinstance(value, list):
            return [self.resolve(item) for item in value]
        if isinstance(value, dict):
            return {key: self.resolve(item) for key, item in value.items()}
        return value

    def _resolve_str(self, text: str) -> str:
        def replace(match: re.Match[str]) -> str:
            if match.group(0) == _ESCAPED:
                return "${"
            name = match.group(1)
            if name not in self._environ:
                raise UnboundVariableError(f"config references ${{{name}}} but it is not set")
            resolved = self._environ[name]
            self._registry.register(f"${{{name}}}", resolved)
            return resolved

        return _REFERENCE.sub(replace, text)

    @staticmethod
    def referenced_variables(value: JsonValue) -> set[str]:
        """Names a config needs, without resolving anything (for ``--dry-run`` and docs)."""
        if isinstance(value, str):
            return {m.group(1) for m in _REFERENCE.finditer(value) if m.group(1)}
        names: set[str] = set()
        if isinstance(value, list):
            for item in value:
                names |= EnvResolver.referenced_variables(item)
        elif isinstance(value, dict):
            for item in value.values():
                names |= EnvResolver.referenced_variables(item)
        return names


def read_config_file(path: Path) -> JsonObject:
    """Reads a ``.toml`` or ``.json`` config file without expanding variables."""
    try:
        if path.suffix == ".toml":
            with path.open("rb") as fh:
                raw: object = tomllib.load(fh)
        elif path.suffix == ".json":
            raw = json.loads(path.read_text(encoding="utf-8"))
        else:
            raise ConfigurationError(
                f"unsupported config format {path.suffix!r}; use .toml or .json"
            )
        return as_json_object(raw)
    except FileNotFoundError as exc:
        raise ConfigurationError(f"config file not found: {path}") from exc
    except (tomllib.TOMLDecodeError, json.JSONDecodeError, TypeError) as exc:
        raise ConfigurationError(f"cannot parse config file {path}: {exc}") from exc


def load_config_file(path: Path, resolver: EnvResolver) -> JsonObject:
    """Reads a config file and expands ``${VAR}`` references."""
    return as_json_object(resolver.resolve(read_config_file(path)))
