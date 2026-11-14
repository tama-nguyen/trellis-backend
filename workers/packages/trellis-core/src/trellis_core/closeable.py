"""Resource lifecycle helpers: idempotent close that never stops at the first failure."""

from abc import ABC, abstractmethod
from collections.abc import Callable
from types import TracebackType
from typing import Self

from trellis_core.logs import get_logger

_log = get_logger(__name__)


class Closeable(ABC):
    """A resource with a ``close()`` that may be called any number of times."""

    @abstractmethod
    def close(self) -> None: ...

    def __enter__(self) -> Self:
        return self

    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        tb: TracebackType | None,
    ) -> None:
        self.close()


class CloseOnce:
    """Runs named close steps once each; a failed step is retried on the next call, others are not.

    Every step runs even if an earlier one failed, and all failures are returned together. Used by
    pipelines so that a broken DLQ producer cannot stop the consumer from committing its offsets.
    """

    def __init__(self) -> None:
        self._steps: list[tuple[str, Callable[[], None]]] = []
        self._done: set[str] = set()

    def add(self, name: str, step: Callable[[], None]) -> None:
        self._steps.append((name, step))

    def run(self) -> list[tuple[str, BaseException]]:
        errors: list[tuple[str, BaseException]] = []
        for name, step in self._steps:
            if name in self._done:
                continue
            try:
                step()
                self._done.add(name)
            except Exception as exc:  # noqa: BLE001 - collect, keep closing the rest
                _log.exception("close step failed", step=name)
                errors.append((name, exc))
        return errors
