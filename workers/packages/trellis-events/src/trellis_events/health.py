"""Health and metrics endpoint inside the worker process (stdlib HTTP server, one daemon thread).

* ``GET /health/live``  200 while the process is running. Liveness must not depend on Kafka or
  Postgres: restarting a worker does not fix a broker outage, it only adds a rebalance.
* ``GET /health/ready`` 200 when every check passes: the source is connected, the inbox database
  answers, and no shutdown was requested; otherwise 503 with the failing checks.
* ``GET /metrics``      Prometheus text format: pipeline counters, readiness, consumer lag.

Binding defaults to 127.0.0.1; a container that must be probed from outside sets ``host`` to
``0.0.0.0`` explicitly. The server is off unless ``[health] enabled = true``.
"""

import json
import threading
from collections.abc import Callable, Mapping
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Annotated, Final

from pydantic import Field

from trellis_core.config import ConfigModel
from trellis_core.json_types import JsonObject
from trellis_core.logs import get_logger
from trellis_events.pipeline.offsets import PartitionId

_log = get_logger(__name__)

LIVE_PATH: Final = "/health/live"
READY_PATH: Final = "/health/ready"
METRICS_PATH: Final = "/metrics"
PROMETHEUS_CONTENT_TYPE: Final = "text/plain; version=0.0.4; charset=utf-8"
JSON_CONTENT_TYPE: Final = "application/json"
DEFAULT_PORT: Final = 9464
METRIC_PREFIX: Final = "trellis_worker"
#: Report counters exported as ``trellis_worker_events_<name>_total``.
COUNTERS: Final = (
    "received",
    "processed",
    "duplicates",
    "filtered",
    "dropped",
    "deferred",
    "retries",
    "dead_lettered",
    "failed",
)


class HealthConfig(ConfigModel):
    enabled: bool = False
    host: str = "127.0.0.1"
    """Loopback by default; set ``0.0.0.0`` only where the orchestrator must reach it."""
    port: Annotated[int, Field(ge=0, le=65535)] = DEFAULT_PORT
    """0 picks a free port (tests)."""
    shutdown_grace_s: Annotated[float, Field(ge=0, le=60)] = 0.0
    """After SIGTERM, keep answering 503 on /health/ready this long before the process exits."""


type Check = Callable[[], bool]


class HealthServer:
    def __init__(
        self,
        config: HealthConfig,
        *,
        consumer: str,
        checks: Mapping[str, Check],
        counters: Callable[[], Mapping[str, int]],
        lag: Callable[[], Mapping[PartitionId, int]],
    ) -> None:
        self._config = config
        self._consumer = consumer
        self._checks = dict(checks)
        self._counters = counters
        self._lag = lag
        self._server: ThreadingHTTPServer | None = None
        self._thread: threading.Thread | None = None

    @property
    def port(self) -> int:
        if self._server is None:
            raise RuntimeError("health server is not running")
        return self._server.server_address[1]

    def start(self) -> None:
        handler = _handler_for(self)
        self._server = ThreadingHTTPServer((self._config.host, self._config.port), handler)
        self._server.daemon_threads = True
        self._thread = threading.Thread(
            target=self._server.serve_forever, name="health-server", daemon=True
        )
        self._thread.start()
        _log.info("health endpoint listening", host=self._config.host, port=self.port)

    def stop(self) -> None:
        if self._server is not None:
            self._server.shutdown()
            self._server.server_close()
            self._server = None

    # -- responses ------------------------------------------------------------------------------

    def readiness(self) -> tuple[bool, JsonObject]:
        results: JsonObject = {}
        for name, check in self._checks.items():
            try:
                results[name] = "UP" if check() else "DOWN"
            except Exception:  # noqa: BLE001 - a crashing check means "not ready", nothing more
                results[name] = "DOWN"
        ready = all(value == "UP" for value in results.values())
        return ready, {"status": "UP" if ready else "DOWN", "checks": results}

    def metrics(self) -> str:
        label = f'consumer="{_escape(self._consumer)}"'
        lines: list[str] = []
        counters = self._counters()
        for name in COUNTERS:
            metric = f"{METRIC_PREFIX}_events_{name}_total"
            lines += [f"# TYPE {metric} counter", f"{metric}{{{label}}} {counters.get(name, 0)}"]
        ready, _ = self.readiness()
        lines += [
            f"# TYPE {METRIC_PREFIX}_ready gauge",
            f"{METRIC_PREFIX}_ready{{{label}}} {int(ready)}",
        ]
        lag_metric = "trellis_consumer_lag"
        lines.append(f"# TYPE {lag_metric} gauge")
        for (topic, partition), lag in sorted(self._lag().items()):
            lines.append(
                f'{lag_metric}{{{label},topic="{_escape(topic)}",partition="{partition}"}} {lag}'
            )
        return "\n".join(lines) + "\n"


def _escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")


def _handler_for(server: HealthServer) -> type[BaseHTTPRequestHandler]:
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            path = self.path.split("?", 1)[0]
            if path == LIVE_PATH:
                self._send(HTTPStatus.OK, JSON_CONTENT_TYPE, json.dumps({"status": "UP"}))
            elif path == READY_PATH:
                ready, body = server.readiness()
                status = HTTPStatus.OK if ready else HTTPStatus.SERVICE_UNAVAILABLE
                self._send(status, JSON_CONTENT_TYPE, json.dumps(body))
            elif path == METRICS_PATH:
                self._send(HTTPStatus.OK, PROMETHEUS_CONTENT_TYPE, server.metrics())
            else:
                self._send(
                    HTTPStatus.NOT_FOUND, JSON_CONTENT_TYPE, json.dumps({"status": "NOT_FOUND"})
                )

        def _send(self, status: HTTPStatus, content_type: str, body: str) -> None:
            data = body.encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)

        def log_message(self, format: str, *args: object) -> None:
            # Probes hit this every few seconds: keep them out of the INFO log.
            _log.debug("health request", request=self.requestline)

    return Handler
