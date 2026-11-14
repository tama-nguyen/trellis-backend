"""Shared pytest setup. Tests here are tooling and contract tests only (decision T5).

The process is forced to UTC before anything else runs: a naive "frozen" time resolved in the local
timezone silently shifts every timestamp on a developer laptop (DataHub hit this with golden files).
"""

import os
import signal
import time
from collections.abc import Iterator

import pytest

from tests.support import CONTRACTS_DIR
from trellis_core.env_vars import reset_settings_cache
from trellis_core.secrets import SecretRegistry


def pytest_configure(config: pytest.Config) -> None:
    os.environ["TZ"] = "UTC"
    time.tzset()
    # Telemetry stays off in tests, whatever the developer's shell exports.
    os.environ["TRELLIS_OTEL_ENABLED"] = "false"
    os.environ["TRELLIS_CONTRACTS_DIR"] = str(CONTRACTS_DIR)
    # Container-mode variables from a developer shell must not change what tests run against.
    for name in (
        "TRELLIS_EVENTS_TRANSPORT",
        "TRELLIS_KAFKA_BOOTSTRAP_SERVERS",
        "TRELLIS_DATABASE_URL",
    ):
        os.environ.pop(name, None)


@pytest.fixture(autouse=True)
def _isolated_process_state() -> Iterator[None]:
    """Settings cache, secret registry and signal handlers are process-wide: reset per test."""
    reset_settings_cache()
    SecretRegistry.default().clear()
    handlers = {sig: signal.getsignal(sig) for sig in (signal.SIGINT, signal.SIGTERM)}
    yield
    for sig, handler in handlers.items():
        signal.signal(sig, handler)
    reset_settings_cache()
    SecretRegistry.default().clear()
