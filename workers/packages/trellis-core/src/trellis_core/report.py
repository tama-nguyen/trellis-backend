"""Structured run reports with bounded memory.

* ``message`` and ``title`` are ``LiteralString``: mypy rejects f-strings there, so every occurrence
  of one kind of problem lands in a single entry and the varying part goes to ``context``. This is
  structured logging enforced by the type checker, and it keeps PII out of titles.
* Contexts are kept in a :class:`LossyList` (reservoir sampling): a million warnings cost a fixed
  amount of memory, yet the report still shows the true total and a fair sample.
* Subclass :class:`Report` as a dataclass and declare counters as fields; :meth:`Report.as_obj`
  turns everything public into JSON.

Pattern adapted from DataHub's ``SourceReport`` and ``LossyList`` (re-implemented).
"""

import dataclasses
import random
import threading
from collections.abc import Callable, Iterator
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from enum import StrEnum
from typing import LiteralString

from trellis_core.json_types import JsonValue
from trellis_core.logs import get_logger

DEFAULT_SAMPLE_SIZE = 10
_log = get_logger(__name__)


class LossyList[T]:
    """Keeps at most ``max_elements`` items (uniform reservoir sample) and counts all of them."""

    def __init__(
        self, max_elements: int = DEFAULT_SAMPLE_SIZE, rng: Callable[[int, int], int] | None = None
    ) -> None:
        if max_elements < 1:
            raise ValueError("max_elements must be >= 1")
        self.max_elements = max_elements
        self.total_elements = 0
        self._items: list[T] = []
        self._randint = rng or random.randint

    def append(self, item: T) -> None:
        self.total_elements += 1
        if len(self._items) < self.max_elements:
            self._items.append(item)
            return
        slot = self._randint(0, self.total_elements - 1)
        if slot < self.max_elements:
            self._items[slot] = item

    def sampled(self) -> bool:
        return self.total_elements > len(self._items)

    def __iter__(self) -> Iterator[T]:
        return iter(list(self._items))

    def __len__(self) -> int:
        return len(self._items)


class Severity(StrEnum):
    INFO = "info"
    WARNING = "warning"
    FAILURE = "failure"


@dataclass
class ReportEntry:
    title: str | None
    message: str
    contexts: LossyList[str] = field(default_factory=LossyList)


@dataclass
class Report:
    """Base report. Thread-safe for ``info``/``warning``/``failure`` and :meth:`increment`."""

    _lock: threading.Lock = field(default_factory=threading.Lock, repr=False)
    _entries: dict[tuple[Severity, str | None, str], ReportEntry] = field(
        default_factory=dict, repr=False
    )

    def info(
        self,
        message: LiteralString,
        context: str | None = None,
        title: LiteralString | None = None,
    ) -> None:
        self._record(Severity.INFO, message, context, title, None)

    def warning(
        self,
        message: LiteralString,
        context: str | None = None,
        title: LiteralString | None = None,
        exc: BaseException | None = None,
    ) -> None:
        self._record(Severity.WARNING, message, context, title, exc)

    def failure(
        self,
        message: LiteralString,
        context: str | None = None,
        title: LiteralString | None = None,
        exc: BaseException | None = None,
    ) -> None:
        self._record(Severity.FAILURE, message, context, title, exc)

    def increment(self, counter: str, by: int = 1) -> None:
        """Adds to an ``int`` field of the subclass, under the report lock."""
        with self._lock:
            current = getattr(self, counter)
            if not isinstance(current, int):
                raise TypeError(f"{counter} is not an int counter")
            setattr(self, counter, current + by)

    def _record(
        self,
        severity: Severity,
        message: str,
        context: str | None,
        title: str | None,
        exc: BaseException | None,
    ) -> None:
        if exc is not None:
            context = f"{context or ''} [{type(exc).__name__}: {exc}]".strip()
        with self._lock:
            key = (severity, title, message)
            entry = self._entries.setdefault(key, ReportEntry(title, message))
            if context is not None:
                entry.contexts.append(context)
        log = _log.error if severity == Severity.FAILURE else _log.warning
        if severity != Severity.INFO:
            log(message, title=title, context=context)

    def entries(self, severity: Severity) -> list[ReportEntry]:
        with self._lock:
            return [e for (s, _, _), e in self._entries.items() if s == severity]

    def as_obj(self) -> dict[str, JsonValue]:
        """Public fields plus grouped info/warnings/failures, as JSON."""
        out: dict[str, JsonValue] = {}
        for f in dataclasses.fields(self):
            if not f.name.startswith("_"):
                out[f.name] = _to_json(getattr(self, f.name))
        for severity in Severity:
            out[f"{severity.value}s"] = [
                {
                    "title": e.title,
                    "message": e.message,
                    "count": e.contexts.total_elements,
                    "sampled": e.contexts.sampled(),
                    "contexts": list(e.contexts),
                }
                for e in self.entries(severity)
            ]
        return out


def _to_json(value: object) -> JsonValue:
    if value is None or isinstance(value, str | bool | int | float):
        return value
    if isinstance(value, timedelta):
        return round(value.total_seconds(), 3)
    if isinstance(value, datetime):
        return value.isoformat()
    if isinstance(value, LossyList):
        return [_to_json(v) for v in value]
    if isinstance(value, dict):
        return {str(k): _to_json(v) for k, v in value.items()}
    if isinstance(value, list | tuple | set | frozenset):
        return [_to_json(v) for v in value]
    if isinstance(value, Report):
        return value.as_obj()
    return str(value)
