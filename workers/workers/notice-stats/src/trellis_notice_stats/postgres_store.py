"""Postgres counter store; joins the transaction of :class:`PostgresInboxGuard`.

Kept in its own module so the in-memory default never imports psycopg.
"""

from importlib.resources import files
from typing import Final

from psycopg_pool import ConnectionPool

from trellis_events.inbox.postgres import PgConnection

_UPSERT: Final = (
    "INSERT INTO notice_stats_counter (counter_key, value) VALUES (%s, %s) "
    "ON CONFLICT (counter_key) DO UPDATE "
    "SET value = notice_stats_counter.value + EXCLUDED.value, updated_at = now()"
)
_SELECT_ALL: Final = "SELECT counter_key, value FROM notice_stats_counter ORDER BY counter_key"


class PostgresCounterStore:
    def __init__(self, pool: ConnectionPool[PgConnection]) -> None:
        self._pool = pool

    def increment(self, tx: PgConnection, key: str, by: int = 1) -> None:
        tx.execute(_UPSERT, (key, by))

    def snapshot(self) -> dict[str, int]:
        with self._pool.connection() as conn:
            rows = conn.execute(_SELECT_ALL).fetchall()
        return {str(key): int(value) for key, value in rows}


def counter_dev_ddl() -> str:
    return (
        files("trellis_notice_stats")
        .joinpath("sql/notice_stats_counter.dev.sql")
        .read_text("utf-8")
    )


def create_schema_for_dev(conn: PgConnection) -> None:
    """Local runs and integration tests only; a deployment owns this table in its migrations."""
    with conn.transaction():
        conn.execute(counter_dev_ddl().encode())
