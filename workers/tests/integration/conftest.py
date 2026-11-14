"""Docker-backed fixtures for ``-m integration``. Each session starts its own Postgres 17 container
on a free localhost port and removes it afterwards; nothing else on the machine is touched."""

import shutil
import socket
import subprocess
import time
import uuid
from collections.abc import Iterator
from dataclasses import dataclass

import psycopg
import pytest

from trellis_events.inbox.postgres import create_schema_for_dev
from trellis_notice_stats.postgres_store import create_schema_for_dev as create_counter_schema

POSTGRES_IMAGE = "postgres:17-alpine"
STARTUP_TIMEOUT_S = 60


@dataclass(frozen=True)
class PostgresContainer:
    name: str
    dsn: str

    def run(self, *args: str) -> None:
        """``docker <args> <container>``: stop, start, pause, unpause."""
        subprocess.run(["docker", *args, self.name], check=True, capture_output=True)

    def wait_ready(self) -> None:
        deadline = time.monotonic() + STARTUP_TIMEOUT_S
        while True:
            try:
                with psycopg.connect(self.dsn, connect_timeout=2) as conn:
                    conn.execute("SELECT 1")
                return
            except psycopg.OperationalError:
                if time.monotonic() > deadline:
                    raise
                time.sleep(0.5)


def _free_port() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port: int = sock.getsockname()[1]
        return port


@pytest.fixture(scope="session")
def postgres() -> Iterator[PostgresContainer]:
    if shutil.which("docker") is None:
        pytest.fail("integration tests need Docker")
    name = f"trellis-py-it-pg-{uuid.uuid4().hex[:8]}"
    port = _free_port()
    password = uuid.uuid4().hex
    subprocess.run(
        [
            "docker", "run", "-d", "--rm", "--name", name,
            "-e", f"POSTGRES_PASSWORD={password}", "-e", "POSTGRES_DB=trellis",
            "-p", f"127.0.0.1:{port}:5432", POSTGRES_IMAGE,
        ],
        check=True,
        capture_output=True,
    )  # fmt: skip
    container = PostgresContainer(
        name, f"postgresql://postgres:{password}@127.0.0.1:{port}/trellis?connect_timeout=3"
    )
    try:
        container.wait_ready()
        with psycopg.connect(container.dsn) as conn:
            create_schema_for_dev(conn)
            create_counter_schema(conn)
        yield container
    finally:
        subprocess.run(["docker", "rm", "-f", name], check=False, capture_output=True)


@pytest.fixture
def consumer_name() -> str:
    """Unique per test, so tests share one database without seeing each other's inbox rows."""
    return f"it-{uuid.uuid4().hex[:10]}"
