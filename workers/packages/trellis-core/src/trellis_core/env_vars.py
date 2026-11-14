"""The only module that reads the process environment.

Every ``TRELLIS_*`` variable is declared once below with its type, default and meaning. Code asks
:func:`get_settings` instead of calling ``os.getenv``; ruff's banned-api rule enforces it everywhere
else. Rules (see ``workers/AGENTS.md``):

* Credentials are ``SecretStr`` and are registered with the secret registry as soon as they are read.
* Nothing ever writes to ``os.environ``: values there leak through ``/proc/<pid>/environ`` and to every
  library in the process. Pass secrets to clients as arguments.
"""

import os
from collections.abc import Mapping
from enum import StrEnum
from functools import lru_cache
from pathlib import Path
from urllib.parse import urlsplit

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict

from trellis_core.secrets import SecretRegistry

ENV_PREFIX = "TRELLIS_"


class LogFormat(StrEnum):
    JSON = "json"
    CONSOLE = "console"


class EventsTransport(StrEnum):
    INMEMORY = "inmemory"
    KAFKA = "kafka"


class TrellisSettings(BaseSettings):
    """Process-level settings, read from ``TRELLIS_*`` environment variables."""

    model_config = SettingsConfigDict(
        env_prefix=ENV_PREFIX,
        extra="ignore",
        hide_input_in_errors=True,
        frozen=True,
    )

    env: str = "dev"
    """Deployment name (dev, staging, prod). Shown in logs; never changes behaviour by itself."""
    debug: bool = False
    """Show validation input in errors and stack traces for expected errors. Never in production."""
    log_level: str = "INFO"
    log_format: LogFormat = LogFormat.JSON
    """``json`` (one object per line, the default for workers) or ``console`` (humans)."""
    service_name: str = "trellis-worker"
    """``service.name`` for logs and OpenTelemetry resources."""
    otel_enabled: bool = False
    """Tracing is off unless explicitly enabled. Trellis sends no product telemetry anywhere."""
    otel_exporter: str = "none"
    """``none``, ``console`` or ``otlp`` (needs the ``trellis-core[otlp]`` extra; endpoint from the
    standard ``OTEL_EXPORTER_OTLP_*`` variables)."""
    masking_enabled: bool = True
    """Mask registered secrets in every log line. Turning it off is for local debugging only."""
    profile: str | None = None
    """CLI profile name; ``--profile`` wins over it."""
    config_dir: Path | None = None
    """Where CLI profiles live (default ``~/.config/trellis``)."""
    contracts_dir: Path | None = None
    """Path to the repo's ``contracts/`` folder; default: search upwards from the working directory."""
    api_url: str | None = None
    """Backend base URL for the SDK; a profile may provide it instead."""
    api_token: SecretStr | None = None
    """Personal access token for the SDK. Prefer a profile file with mode 0600 on laptops."""

    # -- worker defaults, used by `trellis worker run <name>` when no --config file is given ------
    # (container mode; names agreed with docker/compose.yml `x-worker-env`).
    events_transport: EventsTransport = EventsTransport.INMEMORY
    """``kafka`` or ``inmemory``. In-memory without ``--input`` idles until SIGTERM."""
    kafka_bootstrap_servers: str | None = None
    """Required when ``events_transport=kafka``, e.g. ``kafka:29092``."""
    database_url: SecretStr | None = None
    """libpq URL of the worker database (inbox + worker tables); unset = in-memory stores."""
    health_host: str = "127.0.0.1"
    """Bind address of /health/* and /metrics. ``0.0.0.0`` only where Prometheus scrapes it."""
    health_port: int = 9464
    """Port of /health/* and /metrics; ``trellis worker health`` probes it."""


@lru_cache(maxsize=1)
def get_settings() -> TrellisSettings:
    """Reads the environment once per process (tests call :func:`reset_settings_cache`)."""
    settings = TrellisSettings()
    registry = SecretRegistry.default()
    registry.register(f"{ENV_PREFIX}API_TOKEN", settings.api_token)
    registry.register(f"{ENV_PREFIX}DATABASE_URL", settings.database_url)
    if settings.database_url is not None:
        # The password alone also shows up (driver errors echo parts of the URL).
        password = urlsplit(settings.database_url.get_secret_value()).password
        registry.register(f"{ENV_PREFIX}DATABASE_URL password", password)
    return settings


def reset_settings_cache() -> None:
    get_settings.cache_clear()


def environ_snapshot() -> Mapping[str, str]:
    """A read-only copy of the environment for ``${VAR}`` expansion in config files."""
    return dict(os.environ)


def is_ci() -> bool:
    """True on common CI runners (used to keep interactive niceties out of CI logs)."""
    return any(name in os.environ for name in ("CI", "GITLAB_CI", "GITHUB_ACTIONS", "BUILD_ID"))
