"""The Postgres inbox and counter store against a real Postgres 17 (Docker).

Infrastructure verification (decision T5): the delivery guarantees the framework promises hold on
the real database, including two consumers racing on the same event.
"""

import threading
import time
import uuid
from pathlib import Path

import psycopg
import pytest
from pydantic import SecretStr

from tests.integration.conftest import PostgresContainer
from tests.support import EXAMPLE_ENVELOPE
from trellis_cli.main import main
from trellis_core.errors import ExitCode
from trellis_events.inbox.postgres import PostgresInboxGuard
from trellis_notice_stats.action import TOTAL_KEY
from trellis_notice_stats.postgres_store import PostgresCounterStore

pytestmark = pytest.mark.integration

EXAMPLE_EVENT_ID = uuid.UUID("01a1d97f-e500-7d85-a591-84b62830fb11")


@pytest.fixture(autouse=True)
def _empty_counters(postgres: PostgresContainer) -> None:
    with psycopg.connect(postgres.dsn) as conn:
        conn.execute("TRUNCATE notice_stats_counter")


def _inbox_rows(postgres: PostgresContainer, consumer: str) -> int:
    with psycopg.connect(postgres.dsn) as conn:
        row = conn.execute(
            "SELECT count(*) FROM processed_event WHERE consumer_name = %s", (consumer,)
        ).fetchone()
    assert row is not None
    count: int = row[0]
    return count


def _total(postgres: PostgresContainer) -> int:
    with psycopg.connect(postgres.dsn) as conn:
        row = conn.execute(
            "SELECT value FROM notice_stats_counter WHERE counter_key = %s", (TOTAL_KEY,)
        ).fetchone()
    return 0 if row is None else int(row[0])


def _run_worker(tmp_path: Path, consumer: str, *inputs: Path) -> int:
    config = tmp_path / "worker.toml"
    config.write_text(
        f'[pipeline]\nconsumer_name = "{consumer}"\n'
        '[worker]\nstore = "postgres"\npostgres_dsn = "${TRELLIS_IT_DSN}"\n'
    )
    args = ["worker", "run", "notice-stats", "--config", str(config)]
    for path in inputs:
        args += ["--input", str(path)]
    return main(args)


def test_same_event_twice_gives_one_inbox_row_and_one_increment(
    postgres: PostgresContainer, consumer_name: str, tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setenv("TRELLIS_IT_DSN", postgres.dsn)
    assert _run_worker(tmp_path, consumer_name, EXAMPLE_ENVELOPE, EXAMPLE_ENVELOPE) == ExitCode.OK
    assert _inbox_rows(postgres, consumer_name) == 1
    assert _total(postgres) == 1


def test_restart_keeps_state(
    postgres: PostgresContainer, consumer_name: str, tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setenv("TRELLIS_IT_DSN", postgres.dsn)
    assert _run_worker(tmp_path, consumer_name, EXAMPLE_ENVELOPE) == ExitCode.OK
    # A new run is a new pool, a new pipeline, nothing in memory: only the database remembers.
    assert _run_worker(tmp_path, consumer_name, EXAMPLE_ENVELOPE) == ExitCode.OK
    assert _inbox_rows(postgres, consumer_name) == 1
    assert _total(postgres) == 1


def test_concurrent_duplicates_are_applied_exactly_once(
    postgres: PostgresContainer, consumer_name: str
) -> None:
    """Two workers (separate pools) receive the same event at the same moment."""
    workers = [PostgresInboxGuard.connect(SecretStr(postgres.dsn), consumer_name) for _ in range(2)]
    stores = [PostgresCounterStore(guard.pool) for guard in workers]
    start = threading.Barrier(len(workers))
    applied: list[bool] = []
    lock = threading.Lock()

    def deliver(index: int) -> None:
        start.wait()
        with workers[index].transaction(EXAMPLE_EVENT_ID, "corr-race") as tx:
            if tx is not None:
                stores[index].increment(tx, TOTAL_KEY)
                time.sleep(0.5)  # hold the transaction open so the other insert has to wait on it
            with lock:
                applied.append(tx is not None)

    threads = [threading.Thread(target=deliver, args=(i,)) for i in range(len(workers))]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join(timeout=30)
    for guard in workers:
        guard.close()
    assert sorted(applied) == [False, True]
    assert _inbox_rows(postgres, consumer_name) == 1
    assert _total(postgres) == 1


def test_failed_side_effect_rolls_back_the_inbox_row(
    postgres: PostgresContainer, consumer_name: str
) -> None:
    guard = PostgresInboxGuard.connect(SecretStr(postgres.dsn), consumer_name)
    store = PostgresCounterStore(guard.pool)

    def fail_after_insert() -> None:
        with guard.transaction(EXAMPLE_EVENT_ID, None) as tx:
            assert tx is not None
            store.increment(tx, TOTAL_KEY)
            raise RuntimeError("side effect failed after the inbox insert")

    with pytest.raises(RuntimeError):
        fail_after_insert()
    # The retry sees a first delivery again.
    with guard.transaction(EXAMPLE_EVENT_ID, None) as tx:
        assert tx is not None
        store.increment(tx, TOTAL_KEY)
    guard.close()
    assert _inbox_rows(postgres, consumer_name) == 1
    assert _total(postgres) == 1
