"""CLI profiles: named backend targets in ``<config_dir>/profiles.toml``.

Precedence: ``--profile`` > ``TRELLIS_PROFILE`` > ``default``. The file holds tokens, so it must be
readable by its owner only (mode 0600); a looser mode is reported as a warning by ``config show``.

    [default]
    api_url = "http://localhost:8080"
    token = "..."            # personal access token
"""

import stat
import tomllib
from dataclasses import dataclass
from pathlib import Path
from typing import Final

from pydantic import SecretStr

from trellis_core.config import ConfigModel
from trellis_core.env_vars import get_settings
from trellis_core.errors import ConfigurationError
from trellis_core.json_types import as_json_object

DEFAULT_PROFILE: Final = "default"
PROFILES_FILE: Final = "profiles.toml"
_OWNER_ONLY: Final = 0o600


class Profile(ConfigModel):
    api_url: str | None = None
    token: SecretStr | None = None


@dataclass(frozen=True)
class ResolvedProfile:
    name: str
    source: str  # "--profile" | "TRELLIS_PROFILE" | "default"
    path: Path
    profile: Profile
    warnings: tuple[str, ...] = ()


def config_dir() -> Path:
    return get_settings().config_dir or Path.home() / ".config" / "trellis"


def resolve_profile(cli_choice: str | None) -> ResolvedProfile:
    env_choice = get_settings().profile
    if cli_choice:
        name, source = cli_choice, "--profile"
    elif env_choice:
        name, source = env_choice, "TRELLIS_PROFILE"
    else:
        name, source = DEFAULT_PROFILE, "default"
    path = config_dir() / PROFILES_FILE
    if not path.is_file():
        if source != "default":
            raise ConfigurationError(f"profile {name!r} requested but {path} does not exist")
        return ResolvedProfile(name, source, path, Profile())
    warnings: list[str] = []
    if stat.S_IMODE(path.stat().st_mode) & ~_OWNER_ONLY:
        warnings.append(f"{path} is readable by others; run: chmod 600 {path}")
    try:
        document = as_json_object(tomllib.loads(path.read_text(encoding="utf-8")))
    except (tomllib.TOMLDecodeError, TypeError) as exc:
        raise ConfigurationError(f"cannot parse {path}: {exc}") from exc
    section = document.get(name)
    if section is None:
        if source != "default":
            raise ConfigurationError(f"profile {name!r} is not defined in {path}")
        return ResolvedProfile(name, source, path, Profile(), tuple(warnings))
    return ResolvedProfile(name, source, path, Profile.model_validate(section), tuple(warnings))
