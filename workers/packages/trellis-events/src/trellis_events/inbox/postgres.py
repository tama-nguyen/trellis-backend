"""Postgres inbox on the ``processed_event`` table shared with the Java consumers.

Schema ownership: the backend's Liquibase changelog creates the table in every environment.
``sql/processed_event.dev.sql`` next to this module is the same DDL, for local runs and integration
tests against an empty database (:func:`create_schema_for_dev`), and so the two sides can be diffed. Retention: rows older than the
Kafka topic retention + margin (14 days by default) are deleted by a backend job; deleting sooner
would let a redelivered event through.
"""

import threading
import uuid
from collections.abc import Iterator
from concurrent.futures import Future, ThreadPoolExecutor
from concurrent.futures import TimeoutError as FutureTimeout
from contextlib import AbstractContextManager, contextmanager
from importlib.resources import files
from typing import Final

import psycopg
from psycopg import Connection
from psycopg.rows import TupleRow
from psycopg_pool import ConnectionPool, PoolTimeout
from pydantic import SecretStr

#: Dev/test-only DDL, shipped as package data next to this module.
PROCESSED_EVENT_DDL_RESOURCE: Final = "sql/processed_event.dev.sql"

_INSERT_INBOX: Final = (
    "INSERT INTO processed_event (consumer_name, event_id, correlation_id) "
    "VALUES (%s, %s, %s) ON CONFLICT DO NOTHING"
)

type PgConnection = Connection[TupleRow]


class PostgresInboxGuard:
    """Inbox guard backed by a connection pool; the yielded connection is inside the transaction."""

    def __init__(self, pool: ConnectionPool[PgConnection], consumer_name: str) -> None:
        self.consumer_name = consumer_name
        self._pool = pool
        self._check_executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix="inbox-check")
        self._check_lock = threading.Lock()
        self._check_future: Future[bool] | None = None

    @classmethod
    def connect(cls, dsn: SecretStr, consumer_name: str, max_size: int = 4) -> "PostgresInboxGuard":
        pool: ConnectionPool[PgConnection] = ConnectionPool(
            dsn.get_secret_value(), min_size=1, max_size=max_size, open=True
        )
        return cls(pool, consumer_name)

    @property
    def pool(self) -> ConnectionPool[PgConnection]:
        """Shared with stores that read outside the inbox transaction."""
        return self._pool

    def transaction(
        self, event_id: uuid.UUID, correlation_id: str | None
    ) -> AbstractContextManager[PgConnection | None]:
        return self._transaction(event_id, correlation_id)

    @contextmanager
    def _transaction(
        self, event_id: uuid.UUID, correlation_id: str | None
    ) -> Iterator[PgConnection | None]:
        with self._pool.connection() as conn, conn.transaction():
            cursor = conn.execute(_INSERT_INBOX, (self.consumer_name, event_id, correlation_id))
            if cursor.rowcount == 0:
                yield None
                return
            yield conn

    def check(self, timeout_s: float = 2.0) -> bool:
        """Readiness: does the database answer ``SELECT 1`` within ``timeout_s``?

        The query runs on a helper thread: a hung server (network partition, paused host) would
        block a pooled connection with no client-side timeout. A probe still stuck from a previous
        call is reused, so a dead database never piles up threads.
        """
        with self._check_lock:
            if self._check_future is None or self._check_future.done():
                self._check_future = self._check_executor.submit(self._probe, timeout_s)
            future = self._check_future
        try:
            return future.result(timeout=timeout_s)
        except FutureTimeout:
            return False

    def _probe(self, timeout_s: float) -> bool:
        try:
            with self._pool.connection(timeout=timeout_s) as conn:
                conn.execute("SELECT 1")
        except (PoolTimeout, psycopg.Error):
            return False
        return True

    def close(self) -> None:
        self._check_executor.shutdown(wait=False, cancel_futures=True)
        self._pool.close()


def processed_event_dev_ddl() -> str:
    return files("trellis_events.inbox").joinpath(PROCESSED_EVENT_DDL_RESOURCE).read_text("utf-8")


def create_schema_for_dev(conn: PgConnection) -> None:
    """Local runs and integration tests only. Deployed schemas come from Liquibase."""
    with conn.transaction():
        conn.execute(processed_event_dev_ddl().encode())
