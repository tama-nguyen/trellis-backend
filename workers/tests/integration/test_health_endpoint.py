"""/health/live, /health/ready and /metrics of a running worker, with the inbox on real Postgres."""

import json
import threading
import time
import urllib.error
import urllib.request
from collections.abc import Callable, Iterator

import pytest

from tests.integration.conftest import PostgresContainer
from tests.support import EXAMPLE_ENVELOPE
from trellis_core.errors import ExitCode
from trellis_events.pipeline.shutdown import ShutdownSignal
from trellis_events.runner import RunnableWorker, WorkerFileConfig, build_worker

pytestmark = pytest.mark.integration

GRACE_S = 3.0


def _get(port: int, path: str) -> tuple[int, str]:
    try:
        with urllib.request.urlopen(f"http://127.0.0.1:{port}{path}", timeout=10) as response:
            return int(response.status), response.read().decode()
    except urllib.error.HTTPError as error:
        return int(error.code), error.read().decode()


def _wait_for(predicate: Callable[[], bool], timeout_s: float = 15.0) -> None:
    deadline = time.monotonic() + timeout_s
    while not predicate():
        assert time.monotonic() < deadline, "condition not reached in time"
        time.sleep(0.2)


@pytest.fixture
def running_worker(
    postgres: PostgresContainer, consumer_name: str
) -> Iterator[tuple[RunnableWorker, ShutdownSignal, list[ExitCode]]]:
    config = WorkerFileConfig.model_validate(
        {
            "pipeline": {"consumer_name": consumer_name},
            "transport": {
                "type": "inmemory",
                "inputs": [str(EXAMPLE_ENVELOPE), str(EXAMPLE_ENVELOPE)],
                "stop_when_drained": False,
            },
            "worker": {"store": "postgres", "postgres_dsn": postgres.dsn},
            "health": {"enabled": True, "port": 0, "shutdown_grace_s": GRACE_S},
        }
    )
    shutdown = ShutdownSignal()
    worker = build_worker("notice-stats", config, shutdown)
    exit_codes: list[ExitCode] = []
    thread = threading.Thread(target=lambda: exit_codes.append(worker.run()))
    thread.start()
    _wait_for(lambda: worker.health_server is not None)
    yield worker, shutdown, exit_codes
    shutdown.trigger()
    thread.join(timeout=30)


def test_live_ready_metrics_and_readiness_flips(
    postgres: PostgresContainer,
    running_worker: tuple[RunnableWorker, ShutdownSignal, list[ExitCode]],
) -> None:
    worker, shutdown, exit_codes = running_worker
    assert worker.health_server is not None
    port = worker.health_server.port

    assert _get(port, "/health/live")[0] == 200
    _wait_for(lambda: _get(port, "/health/ready")[0] == 200)
    _wait_for(lambda: worker.pipeline.report.received == 2)
    status, metrics = _get(port, "/metrics")
    assert status == 200
    assert "trellis_worker_events_duplicates_total{" in metrics
    assert next(line for line in metrics.splitlines() if "duplicates_total{" in line).endswith(" 1")

    postgres.run("pause")
    try:
        status, body = _get(port, "/health/ready")
        assert status == 503
        assert json.loads(body)["checks"]["worker"] == "DOWN"
        assert _get(port, "/health/live")[0] == 200  # liveness never depends on dependencies
    finally:
        postgres.run("unpause")
    _wait_for(lambda: _get(port, "/health/ready")[0] == 200)

    shutdown.trigger()
    status, body = _get(port, "/health/ready")  # inside the grace period
    assert status == 503
    assert json.loads(body)["checks"]["running"] == "DOWN"
    _wait_for(lambda: exit_codes == [ExitCode.OK], timeout_s=GRACE_S + 15)
