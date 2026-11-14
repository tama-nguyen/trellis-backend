"""Trellis local stack helper (DataHub `datahub docker quickstart`, much smaller). Stdlib only.

Wraps `docker compose -f build-logic/docker/compose.yml` and adds per-worktree instances: each git worktree gets
a slot N (0 for the main checkout), its own compose project name and host ports shifted by N*1000, so
several worktrees can run the stack side by side.

    build-logic/scripts/dev/trellis-dev.sh up [--profile minimal|app|full|debug] [--build]
    build-logic/scripts/dev/trellis-dev.sh status | ports | down
    build-logic/scripts/dev/trellis-dev.sh logs <service> [-f]
    build-logic/scripts/dev/trellis-dev.sh rebuild <service>
    build-logic/scripts/dev/trellis-dev.sh reset [--yes]       # down -v for THIS slot's project only
    build-logic/scripts/dev/trellis-dev.sh slot                # show this worktree's slot

Global: --slot N (or TRELLIS_SLOT=N) overrides the worktree's slot.
"""

from __future__ import annotations

import argparse
import json
import os
import shlex
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
COMPOSE_FILE = REPO / "build-logic" / "docker" / "compose.yml"
PROFILES = ("minimal", "app", "full", "debug")
DEFAULT_PROFILE = "minimal"
SLOT_FILE_NAME = "trellis-dev-slot"
SLOT_STRIDE = 1000
MAX_SLOT = 4
PROJECT_BASE = "trellis"

# variable -> (default host port, service, what, url scheme or "" when not HTTP)
PORTS: dict[str, tuple[int, str, str, str]] = {
    "TRELLIS_PG_PORT": (15432, "postgres", "PostgreSQL", ""),
    "TRELLIS_WEB_PORT": (13000, "web", "Next.js", "http"),
    "TRELLIS_BACKEND_PORT": (18080, "backend", "API", "http"),
    "TRELLIS_BACKEND_MGMT_PORT": (18081, "backend", "actuator", "http"),
    "TRELLIS_KAFKA_PORT": (19092, "kafka", "Kafka (EXTERNAL listener)", ""),
    "TRELLIS_REDIS_PORT": (16379, "redis", "Redis", ""),
    "TRELLIS_MINIO_PORT": (19000, "minio", "S3 API", "http"),
    "TRELLIS_MINIO_CONSOLE_PORT": (19001, "minio", "MinIO console", "http"),
    "TRELLIS_KEYCLOAK_PORT": (18180, "keycloak", "Keycloak", "http"),
    "TRELLIS_OTLP_GRPC_PORT": (14317, "otel-collector", "OTLP gRPC", ""),
    "TRELLIS_OTLP_HTTP_PORT": (14318, "otel-collector", "OTLP HTTP", "http"),
    "TRELLIS_PROMETHEUS_PORT": (19090, "prometheus", "Prometheus", "http"),
    "TRELLIS_GRAFANA_PORT": (13300, "grafana", "Grafana", "http"),
    "TRELLIS_TEMPO_PORT": (13200, "tempo", "Tempo", "http"),
    "TRELLIS_LOKI_PORT": (13100, "loki", "Loki", "http"),
    "TRELLIS_BACKEND_DEBUG_PORT": (15005, "backend-debug", "JDWP", ""),
    "TRELLIS_WORKER_DEBUG_PORT": (15678, "worker-debug", "debugpy", ""),
}


def fail(msg: str) -> None:
    print(f"trellis-dev: {msg}", file=sys.stderr)
    sys.exit(2)


# ------------------------------------------------------------------ slots
def git(*args: str) -> str:
    out = subprocess.run(["git", *args], cwd=REPO, capture_output=True, text=True, check=False)
    return out.stdout.strip() if out.returncode == 0 else ""


def git_dir() -> Path | None:
    """This worktree's private git dir: the slot file lives there, so it is never committed."""
    d = git("rev-parse", "--absolute-git-dir")
    return Path(d) if d else None


def is_main_worktree() -> bool:
    common = git("rev-parse", "--path-format=absolute", "--git-common-dir")
    own = git("rev-parse", "--absolute-git-dir")
    return not common or common == own


def other_worktree_slots() -> set[int]:
    slots: set[int] = set()
    for line in git("worktree", "list", "--porcelain").splitlines():
        if not line.startswith("worktree "):
            continue
        path = line.split(" ", 1)[1]
        d = subprocess.run(["git", "-C", path, "rev-parse", "--absolute-git-dir"],
                           capture_output=True, text=True, check=False).stdout.strip()
        f = Path(d) / SLOT_FILE_NAME if d else None
        if f and f.exists():
            try:
                slots.add(int(f.read_text().strip()))
            except ValueError:
                pass
    return slots


def resolve_slot(cli_slot: int | None) -> int:
    if cli_slot is not None:
        slot = cli_slot
    elif os.environ.get("TRELLIS_SLOT"):
        slot = int(os.environ["TRELLIS_SLOT"])
    else:
        d = git_dir()
        f = d / SLOT_FILE_NAME if d else None
        if f and f.exists():
            slot = int(f.read_text().strip())
        elif is_main_worktree():
            slot = 0
        else:
            taken = other_worktree_slots() | {0}
            free = [s for s in range(1, MAX_SLOT + 1) if s not in taken]
            if not free:
                fail(f"all slots 1..{MAX_SLOT} are taken by other worktrees; pass --slot")
            slot = free[0]
            if f:
                f.write_text(f"{slot}\n")
                print(f"trellis-dev: this worktree now uses slot {slot} (saved in {f})")
    if not 0 <= slot <= MAX_SLOT:
        fail(f"slot must be 0..{MAX_SLOT}")
    return slot


def project_name(slot: int) -> str:
    return PROJECT_BASE if slot == 0 else f"{PROJECT_BASE}-s{slot}"


def slot_env(slot: int, profile: str | None = None) -> dict[str, str]:
    env = dict(os.environ)
    env["COMPOSE_PROJECT_NAME"] = project_name(slot)
    for var, (port, *_rest) in PORTS.items():
        env.setdefault(var, str(port + slot * SLOT_STRIDE))
    if profile in ("full", "debug"):
        # Kafka and MinIO are running: use them, and let `setup` create topics and the bucket.
        env.setdefault("TRELLIS_EVENTS_TRANSPORT", "kafka")
        env.setdefault("TRELLIS_SETUP_KAFKA_TOPICS", "true")
        env.setdefault("TRELLIS_SETUP_S3_BUCKET", "true")
    return env


# ------------------------------------------------------------------ compose
def compose(env: dict[str, str], *args: str, profiles: tuple[str, ...] = ("*",), check: bool = True) -> int:
    cmd = ["docker", "compose", "-f", str(COMPOSE_FILE)]
    for p in profiles:
        cmd += ["--profile", p]
    cmd += list(args)
    print("+ " + shlex.join(cmd), file=sys.stderr)
    rc = subprocess.run(cmd, env=env, check=False).returncode
    if check and rc != 0:
        sys.exit(rc)
    return rc


def cmd_up(a: argparse.Namespace, slot: int) -> None:
    env = slot_env(slot, a.profile)
    extra = ["--build"] if a.build else []
    compose(env, "up", "--detach", "--wait", "--wait-timeout", str(a.timeout), *extra, profiles=(a.profile,))
    print(f"\nprofile {a.profile} is up as project {env['COMPOSE_PROJECT_NAME']} (slot {slot})")
    print_ports(slot, only_running=True)


def cmd_down(_a: argparse.Namespace, slot: int) -> None:
    compose(slot_env(slot), "down", "--remove-orphans")


def cmd_status(_a: argparse.Namespace, slot: int) -> None:
    env = slot_env(slot)
    print(f"project {env['COMPOSE_PROJECT_NAME']} (slot {slot})")
    out = subprocess.run(
        ["docker", "compose", "-f", str(COMPOSE_FILE), "--profile", "*", "ps", "--all", "--format", "json"],
        env=env, capture_output=True, text=True, check=False,
    )
    if out.returncode != 0:
        fail(out.stderr.strip() or "docker compose ps failed")
    rows = [json.loads(line) for line in out.stdout.splitlines() if line.strip()]
    if not rows:
        print("  nothing running")
        return
    for r in sorted(rows, key=lambda r: r.get("Service", "")):
        health = r.get("Health") or "-"
        print(f"  {r.get('Service', '?'):16s} {r.get('State', '?'):10s} {health:10s} {r.get('Status', '')}")


def cmd_logs(a: argparse.Namespace, slot: int) -> None:
    args = ["logs", "--tail", str(a.tail)] + (["--follow"] if a.follow else []) + [a.service]
    compose(slot_env(slot), *args)


def cmd_rebuild(a: argparse.Namespace, slot: int) -> None:
    compose(slot_env(slot), "up", "--detach", "--build", "--no-deps", "--force-recreate", "--wait", a.service)


def cmd_reset(a: argparse.Namespace, slot: int) -> None:
    env = slot_env(slot)
    project = env["COMPOSE_PROJECT_NAME"]
    if not a.yes:
        if not sys.stdin.isatty():
            fail("reset deletes volumes; pass --yes when not running interactively")
        answer = input(f"Delete all containers AND volumes of compose project '{project}'? Type the project name: ")
        if answer.strip() != project:
            print("aborted")
            return
    # Scoped by project name: other projects on this machine and other slots are untouched.
    compose(env, "down", "--volumes", "--remove-orphans")


def print_ports(slot: int, only_running: bool = False) -> None:
    env = slot_env(slot)
    running: set[str] = set()
    if only_running:
        out = subprocess.run(
            ["docker", "compose", "-f", str(COMPOSE_FILE), "--profile", "*", "ps", "--services", "--status", "running"],
            env=env, capture_output=True, text=True, check=False,
        )
        running = set(out.stdout.split())
    bind = env.get("TRELLIS_BIND", "127.0.0.1")
    print(f"{'service':16s} {'what':26s} {'host port':>9s}  {'variable':28s} url")
    for var, (_port, service, what, scheme) in PORTS.items():
        if only_running and service not in running:
            continue
        port = env[var]
        url = f"{scheme}://{bind}:{port}" if scheme else ""
        print(f"{service:16s} {what:26s} {port:>9s}  {var:28s} {url}")


def cmd_ports(_a: argparse.Namespace, slot: int) -> None:
    print(f"slot {slot}, project {project_name(slot)}, offset +{slot * SLOT_STRIDE}")
    print_ports(slot)


def cmd_slot(_a: argparse.Namespace, slot: int) -> None:
    print(f"slot {slot}  project {project_name(slot)}  port offset +{slot * SLOT_STRIDE}")


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="trellis-dev", description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--slot", type=int, help=f"instance slot 0..{MAX_SLOT} (default: this worktree's)")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("up", help="start a profile and wait until healthy")
    p.add_argument("--profile", choices=PROFILES, default=DEFAULT_PROFILE)
    p.add_argument("--build", action="store_true", help="build app images first")
    p.add_argument("--timeout", type=int, default=300, help="seconds to wait for health (default 300)")
    p.set_defaults(fn=cmd_up)
    sub.add_parser("down", help="stop and remove containers (volumes kept)").set_defaults(fn=cmd_down)
    sub.add_parser("status", help="containers of this slot and their health").set_defaults(fn=cmd_status)
    p = sub.add_parser("logs", help="logs of one service")
    p.add_argument("service")
    p.add_argument("-f", "--follow", action="store_true")
    p.add_argument("--tail", type=int, default=200)
    p.set_defaults(fn=cmd_logs)
    p = sub.add_parser("rebuild", help="rebuild and recreate one service")
    p.add_argument("service")
    p.set_defaults(fn=cmd_rebuild)
    p = sub.add_parser("reset", help="down -v for this slot's project only (asks first)")
    p.add_argument("--yes", action="store_true", help="do not ask")
    p.set_defaults(fn=cmd_reset)
    sub.add_parser("ports", help="host port map of this slot").set_defaults(fn=cmd_ports)
    sub.add_parser("slot", help="show this worktree's slot and project name").set_defaults(fn=cmd_slot)

    a = parser.parse_args(argv)
    a.fn(a, resolve_slot(a.slot))


if __name__ == "__main__":
    main()
