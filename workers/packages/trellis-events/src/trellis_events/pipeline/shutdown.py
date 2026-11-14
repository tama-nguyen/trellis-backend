"""Graceful shutdown for containers.

Install the handlers **before** loading config: as PID 1 in a container, a signal with no handler is
ignored by the kernel, so a slow config load would make ``docker stop`` wait for SIGKILL.

* First SIGTERM/SIGINT: set the flag and call every registered ``request_stop`` (flag-only work,
  safe inside a signal handler). The pipeline finishes the current event, commits, closes, exits 0.
* Second signal: raise ``KeyboardInterrupt`` so the process exits 130 without waiting.
"""

import signal
import threading
from collections.abc import Callable
from types import FrameType


class ShutdownSignal:
    def __init__(self) -> None:
        self._event = threading.Event()
        self._callbacks: list[Callable[[], None]] = []
        self._signals_received = 0
        self.signal_name: str | None = None

    def install(
        self, signals: tuple[signal.Signals, ...] = (signal.SIGTERM, signal.SIGINT)
    ) -> None:
        """Main thread only (a Python rule for ``signal.signal``)."""
        for sig in signals:
            signal.signal(sig, self._handle)

    def on_stop(self, callback: Callable[[], None]) -> None:
        self._callbacks.append(callback)
        if self._event.is_set():
            callback()

    def trigger(self) -> None:
        """Programmatic stop (tests, an admin endpoint)."""
        if self._event.is_set():
            return
        self._event.set()
        for callback in self._callbacks:
            callback()

    @property
    def requested(self) -> bool:
        return self._event.is_set()

    def wait(self, timeout: float | None = None) -> bool:
        return self._event.wait(timeout)

    def _handle(self, signum: int, _frame: FrameType | None) -> None:
        self._signals_received += 1
        if self._signals_received > 1:
            raise KeyboardInterrupt
        # No logging here: the handler may interrupt a thread that holds the logging lock.
        self.signal_name = signal.Signals(signum).name
        self.trigger()
