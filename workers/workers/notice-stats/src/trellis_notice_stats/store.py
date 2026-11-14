"""Counter stores. Each store pairs with the inbox guard whose transaction it joins."""

import threading
from typing import Protocol

from trellis_events.inbox.memory import InMemoryTx


class CounterStore[TxT](Protocol):
    def increment(self, tx: TxT, key: str, by: int = 1) -> None:
        """Adds ``by`` inside ``tx``: the change commits together with the inbox row."""
        ...

    def snapshot(self) -> dict[str, int]: ...


class InMemoryCounterStore:
    """Applies increments when the in-memory inbox transaction commits (see InMemoryInboxGuard)."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._counts: dict[str, int] = {}

    def increment(self, tx: InMemoryTx, key: str, by: int = 1) -> None:
        tx.add(key, by)

    def on_commit(self, tx: InMemoryTx) -> None:
        with self._lock:
            for key, by in tx.effects:
                self._counts[key] = self._counts.get(key, 0) + by

    def snapshot(self) -> dict[str, int]:
        with self._lock:
            return dict(sorted(self._counts.items()))
