"""Run tasks in parallel across keys, strictly in order within a key, with bounded backpressure.

* At most one task per key runs at a time; tasks of one key run in submission order. Keys are the
  envelope ordering key (aggregate id), so one aggregate's events are never reordered.
* ``submit`` blocks once ``max_pending`` tasks are queued or running: a slow action slows the
  consumer down instead of buffering without limit (and the broker keeps the backlog, not our heap).
* Each task runs in a copy of the submitter's ``contextvars`` context, so log context and the
  active span follow the task into the worker thread.

Pattern adapted from DataHub's ``datahub.utilities.partition_executor.PartitionExecutor``
(re-implemented, without the batching variant).
"""

import contextvars
import threading
from collections import deque
from collections.abc import Callable
from concurrent.futures import Future, ThreadPoolExecutor
from dataclasses import dataclass


@dataclass
class _Task:
    fn: Callable[[], None]
    future: "Future[None]"
    context: contextvars.Context


class PartitionExecutor:
    def __init__(self, max_workers: int, max_pending: int) -> None:
        if max_workers < 1 or max_pending < max_workers:
            raise ValueError("need 1 <= max_workers <= max_pending")
        self._pool = ThreadPoolExecutor(max_workers=max_workers, thread_name_prefix="partition")
        self._slots = threading.BoundedSemaphore(max_pending)
        self._lock = threading.Lock()
        self._queues: dict[str, deque[_Task]] = {}
        self._closed = False

    def submit(self, key: str, fn: Callable[[], None]) -> "Future[None]":
        """Queues ``fn`` behind earlier tasks of ``key``; blocks while ``max_pending`` is reached."""
        self._slots.acquire()
        task = _Task(fn, Future(), contextvars.copy_context())
        with self._lock:
            if self._closed:
                self._slots.release()
                raise RuntimeError("executor is shut down")
            queue = self._queues.get(key)
            if queue is not None:
                queue.append(task)
                return task.future
            self._queues[key] = deque()
        self._pool.submit(self._drain, key, task)
        return task.future

    def _drain(self, key: str, task: _Task) -> None:
        current: _Task | None = task
        while current is not None:
            try:
                current.context.run(current.fn)
                current.future.set_result(None)
            except BaseException as exc:  # noqa: BLE001 - delivered to the caller via the future
                current.future.set_exception(exc)
            finally:
                self._slots.release()
            with self._lock:
                queue = self._queues[key]
                if queue:
                    current = queue.popleft()
                else:
                    del self._queues[key]
                    current = None

    def pending_keys(self) -> int:
        with self._lock:
            return len(self._queues)

    def shutdown(self, wait: bool = True) -> None:
        """Stops accepting tasks; with ``wait`` every queued task still runs before returning."""
        with self._lock:
            self._closed = True
        self._pool.shutdown(wait=wait)
