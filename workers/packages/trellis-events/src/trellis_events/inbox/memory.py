"""In-memory inbox for tests and local runs. Not durable: a restart forgets every event."""

import threading
import uuid
from collections.abc import Iterator
from contextlib import AbstractContextManager, contextmanager
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Protocol


@dataclass
class InMemoryTx:
    """Stand-in for a database transaction: side effects registered here apply on commit only."""

    effects: list[tuple[str, int]] = field(default_factory=list)

    def add(self, key: str, by: int) -> None:
        self.effects.append((key, by))


class InMemoryInboxGuard:
    def __init__(self, consumer_name: str) -> None:
        self.consumer_name = consumer_name
        self._lock = threading.Lock()
        self._processed: dict[tuple[str, uuid.UUID], datetime] = {}
        self._in_progress: set[tuple[str, uuid.UUID]] = set()
        self.commit_listeners: list[InMemoryCommitListener] = []

    def transaction(
        self, event_id: uuid.UUID, correlation_id: str | None
    ) -> AbstractContextManager[InMemoryTx | None]:
        return self._transaction(event_id)

    @contextmanager
    def _transaction(self, event_id: uuid.UUID) -> Iterator[InMemoryTx | None]:
        key = (self.consumer_name, event_id)
        with self._lock:
            # In progress counts as seen: two copies of one event must not run side by side.
            duplicate = key in self._processed or key in self._in_progress
            if not duplicate:
                self._in_progress.add(key)
        if duplicate:
            yield None
            return
        tx = InMemoryTx()
        try:
            yield tx
        except BaseException:
            with self._lock:
                self._in_progress.discard(key)
            raise
        with self._lock:
            self._in_progress.discard(key)
            self._processed[key] = datetime.now(UTC)
            for listener in self.commit_listeners:
                listener.on_commit(tx)

    def processed_count(self) -> int:
        with self._lock:
            return len(self._processed)


class InMemoryCommitListener(Protocol):
    """Receives the side effects of a committed :class:`InMemoryTx` (e.g. a counter store)."""

    def on_commit(self, tx: InMemoryTx) -> None: ...
