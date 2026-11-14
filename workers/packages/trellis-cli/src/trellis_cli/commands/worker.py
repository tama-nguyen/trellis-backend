"""``trellis worker list`` and ``trellis worker run <name>``."""

import json
import urllib.error
import urllib.request
from http import HTTPStatus
from pathlib import Path

import click

from trellis_cli.output import CliState, announce, emit
from trellis_core.env_resolver import EnvResolver, read_config_file
from trellis_core.env_vars import EventsTransport, environ_snapshot, get_settings
from trellis_core.errors import ConfigurationError, ExitCode
from trellis_core.json_types import JsonObject, JsonValue, as_json_object
from trellis_core.plugins import get_plugin_meta
from trellis_events.health import READY_PATH
from trellis_events.pipeline.shutdown import ShutdownSignal
from trellis_events.runner import WorkerFileConfig, build_worker, describe_config
from trellis_events.worker import worker_registry


@click.group("worker")
def worker_group() -> None:
    """List and run event workers (plugins in the `trellis.workers` entry point group)."""


@worker_group.command("list")
@click.pass_obj
def list_command(state: CliState) -> int:
    """List installed workers; broken ones are shown as disabled with the reason."""
    rows: list[JsonValue] = []
    lines: list[str] = []
    registry = worker_registry()
    for status in registry.summary(load=True):
        meta = None if status.state != "loaded" else get_plugin_meta(registry.get(status.name))
        rows.append(
            {
                "name": status.name,
                "state": status.state,
                "reason": status.reason,
                "description": meta.description if meta else None,
                "status": meta.status.name if meta else None,
            }
        )
        lines.append(
            f"{status.name:<24} {status.state:<9} {status.reason or (meta.description if meta else '')}"
        )
    emit(state, {"workers": rows}, "\n".join(lines) or "no workers installed")
    return ExitCode.OK


def _environment_config(name: str, one_shot: bool) -> JsonObject:
    """Container mode (no --config): everything from TRELLIS_* variables (see env_vars)."""
    settings = get_settings()
    transport: JsonObject = {"type": settings.events_transport.value}
    if settings.events_transport == EventsTransport.KAFKA:
        if not settings.kafka_bootstrap_servers:
            raise ConfigurationError(
                "TRELLIS_EVENTS_TRANSPORT=kafka needs TRELLIS_KAFKA_BOOTSTRAP_SERVERS"
            )
        transport["bootstrap_servers"] = settings.kafka_bootstrap_servers
    return {
        "pipeline": {"consumer_name": name},
        "transport": transport,
        # A long-running worker serves /health/*; a one-shot --input run does not need it.
        "health": {
            "enabled": not one_shot,
            "host": settings.health_host,
            "port": settings.health_port,
        },
    }


def _load_config(name: str, config_path: Path | None, one_shot: bool) -> JsonObject:
    if config_path is None:
        return _environment_config(name, one_shot)
    raw = read_config_file(config_path)
    return as_json_object(EnvResolver(environ_snapshot()).resolve(raw))


@worker_group.command("run")
@click.argument("name")
@click.option(
    "--config",
    "config_path",
    type=click.Path(path_type=Path, dir_okay=False),
    help="Worker config (.toml/.json); ${VAR} is expanded. Default: TRELLIS_* environment.",
)
@click.option(
    "--input",
    "inputs",
    multiple=True,
    type=click.Path(path_type=Path, dir_okay=False),
    help="Envelope file to feed (repeatable; implies the in-memory transport).",
)
@click.option(
    "--dry-run", is_flag=True, help="Validate config and print the plan; consume nothing."
)
@click.pass_obj
def run_command(
    state: CliState, name: str, config_path: Path | None, inputs: tuple[Path, ...], dry_run: bool
) -> int:
    """Run worker NAME until SIGTERM, or until the --input files are processed.

    Without --config the worker is configured from the environment (container mode):
    TRELLIS_EVENTS_TRANSPORT, TRELLIS_KAFKA_BOOTSTRAP_SERVERS, TRELLIS_DATABASE_URL,
    TRELLIS_HEALTH_HOST/PORT. The in-memory transport without --input idles until SIGTERM.

    Exit 0 after a clean stop, 1 if the pipeline failed (THROW mode, DLQ unavailable), 78 for bad
    config, 130 if interrupted twice.
    """
    # Signal handlers first: as PID 1 an unhandled SIGTERM is ignored while config loads.
    shutdown = ShutdownSignal()
    shutdown.install()
    document = _load_config(name, config_path, one_shot=bool(inputs))
    if inputs:
        document["transport"] = {"type": "inmemory", "inputs": [str(p) for p in inputs]}
    config = WorkerFileConfig.model_validate(document)
    plan: JsonObject = {
        "worker": name,
        "consumer": config.pipeline.consumer_name,
        "transport": config.transport.type,
        "failure_mode": config.pipeline.failure_mode.value,
        "profile": state.profile,
        "config": describe_config(config, name),
    }
    if dry_run:
        emit(
            state,
            {"dry_run": True, "plan": plan},
            f"dry run: would start {name} ({plan['transport']})",
        )
        return ExitCode.OK
    announce(
        f"running worker {name} as consumer {config.pipeline.consumer_name!r} "
        f"on transport {config.transport.type!r}"
    )
    runnable = build_worker(name, config, shutdown)
    exit_code = runnable.run()
    result: JsonObject = {
        "worker": name,
        "exit_code": int(exit_code),
        "report": runnable.pipeline.report.as_obj(),
        "status": runnable.final_status,
    }
    if runnable.memory_dlq is not None:
        result["dlq"] = [
            {
                "topic": r.topic,
                "error": f"{type(r.error).__name__}: {r.error}",
                "attempts": r.attempts,
            }
            for r in runnable.memory_dlq.records
        ]
    report = runnable.pipeline.report
    emit(
        state,
        result,
        f"{name}: received={report.received} processed={report.processed} "
        f"duplicates={report.duplicates} dead_lettered={report.dead_lettered} exit={int(exit_code)}",
    )
    return exit_code


@worker_group.command("health")
@click.argument("name")
@click.option("--port", type=int, help="Health port (default: TRELLIS_HEALTH_PORT).")
@click.option("--timeout", "timeout_s", type=float, default=3.0, show_default=True)
@click.pass_obj
def health_command(state: CliState, name: str, port: int | None, timeout_s: float) -> int:
    """Probe the running worker's /health/ready (for container HEALTHCHECK).

    Exit 0 when ready: the consumer loop polled recently, the broker and the inbox database answer
    and no shutdown is in progress. Exit 1 otherwise (never 2: Docker reserves it).
    """
    settings = get_settings()
    host = "127.0.0.1" if settings.health_host in ("0.0.0.0", "::") else settings.health_host  # noqa: S104 - comparing, not binding
    url = f"http://{host}:{port or settings.health_port}{READY_PATH}"
    status, body = _probe(url, timeout_s)
    healthy = status == HTTPStatus.OK
    data: JsonObject = {"worker": name, "url": url, "healthy": healthy, "status": status}
    try:
        data["response"] = as_json_object(json.loads(body)) if body else None
    except (ValueError, TypeError):
        data["response"] = body
    emit(state, data, f"{name}: {'healthy' if healthy else 'unhealthy'} ({status}) {body}")
    return ExitCode.OK if healthy else ExitCode.FAILURE


def _probe(url: str, timeout_s: float) -> tuple[int, str]:
    try:
        with urllib.request.urlopen(url, timeout=timeout_s) as response:  # noqa: S310 - local http URL built above
            return int(response.status), response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as error:
        return int(error.code), error.read().decode("utf-8", errors="replace")
    except (urllib.error.URLError, OSError) as error:
        return 0, f"unreachable: {error}"
