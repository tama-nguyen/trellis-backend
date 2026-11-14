"""Per-partition commit watermark for at-least-once delivery with out-of-order completion.

Kafka keeps one committed offset per partition: committing N means "everything below N is done".
When events of one partition finish out of order (parallel keys, deferred acks, retries), the safe
position to commit is the lowest offset still in flight. This tracker computes exactly that, so a
failed or deferred event is never skipped by a later success.
"""

import threading
from dataclasses import dataclass, field

type PartitionId = tuple[str, int]


@dataclass
class _PartitionState:
    in_flight: set[int] = field(default_factory=set)
    highest_seen: int = -1
    committed: int | None = None


class OffsetTracker:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._partitions: dict[PartitionId, _PartitionState] = {}

    def started(self, tp: PartitionId, offset: int) -> None:
        with self._lock:
            state = self._partitions.setdefault(tp, _PartitionState())
            state.in_flight.add(offset)
            state.highest_seen = max(state.highest_seen, offset)

    def done(self, tp: PartitionId, offset: int) -> None:
        with self._lock:
            state = self._partitions.get(tp)
            if state is not None:
                state.in_flight.discard(offset)

    def _position(self, state: _PartitionState) -> int | None:
        if state.highest_seen < 0:
            return None
        return min(state.in_flight) if state.in_flight else state.highest_seen + 1

    def pending_commits(self) -> dict[PartitionId, int]:
        """Positions (next offset to read) that advanced since the last :meth:`mark_committed`."""
        with self._lock:
            out: dict[PartitionId, int] = {}
            for tp, state in self._partitions.items():
                position = self._position(state)
                if position is not None and position != state.committed:
                    out[tp] = position
            return out

    def mark_committed(self, positions: dict[PartitionId, int]) -> None:
        with self._lock:
            for tp, position in positions.items():
                if tp in self._partitions:
                    self._partitions[tp].committed = position

    def forget(self, partitions: list[PartitionId]) -> None:
        """After a rebalance revoked these partitions; another consumer owns them now."""
        with self._lock:
            for tp in partitions:
                self._partitions.pop(tp, None)

    def position(self, tp: PartitionId) -> int | None:
        """Next offset that is not yet done (what a commit now would store), if any was seen."""
        with self._lock:
            state = self._partitions.get(tp)
            return None if state is None else self._position(state)

    def in_flight_count(self) -> int:
        with self._lock:
            return sum(len(s.in_flight) for s in self._partitions.values())
