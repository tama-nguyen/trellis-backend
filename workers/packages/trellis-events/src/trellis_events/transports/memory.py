"""In-memory transport for tests and local runs (``--transport inmemory``).

Same delivery contract as Kafka (ack after success, offsets per partition), no broker needed.
"""

import json
import queue
import threading
from collections.abc import Iterable, Iterator
from pathlib import Path
from typing import Literal

from trellis_core.config import ConfigModel
from trellis_core.errors import InputError
from trellis_events.constants import Headers
from trellis_events.envelope import Envelope
from trellis_events.pipeline.model import Delivery, DlqRecord
from trellis_events.pipeline.offsets import OffsetTracker, PartitionId

_POLL_SECONDS = 0.2


class InMemoryTransportConfig(ConfigModel):
    type: Literal["inmemory"] = "inmemory"
    inputs: list[Path] = []
    """Envelope files (``.json`` = one envelope, ``.jsonl`` = one per line), delivered in order."""
    stop_when_drained: bool | None = None
    """Return from ``events()`` once every input was delivered. Default: yes when ``inputs`` are
    given (a one-shot run), no otherwise (idle until SIGTERM, as a container must)."""

    def drains(self) -> bool:
        return bool(self.inputs) if self.stop_when_drained is None else self.stop_when_drained


def delivery_from_envelope(envelope: Envelope, offset: int, partition: int = 0) -> Delivery:
    """A delivery as the Java relay would produce it: topic = eventType, key = ordering key."""
    headers = {
        Headers.EVENT_ID: str(envelope.event_id).encode(),
        Headers.EVENT_TYPE: envelope.event_type.encode(),
        Headers.EVENT_VERSION: str(envelope.event_version).encode(),
    }
    if envelope.correlation_id:
        headers[Headers.CORRELATION_ID] = envelope.correlation_id.encode()
    if envelope.traceparent:
        headers[Headers.TRACEPARENT] = envelope.traceparent.encode()
    return Delivery(
        value=envelope.to_bytes(),
        key=envelope.ordering_key.encode(),
        headers=headers,
        topic=envelope.event_type,
        partition=partition,
        offset=offset,
    )


def read_raw_inputs(paths: Iterable[Path]) -> list[bytes]:
    """Raw bodies from files, unvalidated: an invalid one must reach the pipeline (and the DLQ)."""
    bodies: list[bytes] = []
    for path in paths:
        try:
            text = path.read_text(encoding="utf-8")
        except OSError as exc:
            raise InputError(f"cannot read input {path}: {exc}") from exc
        if path.suffix == ".jsonl":
            bodies.extend(line.encode() for line in text.splitlines() if line.strip())
        else:
            # Re-serialise compactly so the body is one line, as on the broker.
            try:
                bodies.append(json.dumps(json.loads(text), separators=(",", ":")).encode())
            except json.JSONDecodeError:
                bodies.append(text.encode())
    return bodies


class InMemorySource:
    def __init__(self, *, stop_when_drained: bool = True, topic: str = "inmemory") -> None:
        self._queue: queue.Queue[Delivery] = queue.Queue()
        self._stop = threading.Event()
        self._stop_when_drained = stop_when_drained
        self._topic = topic
        self._next_offset = 0
        self._lock = threading.Lock()
        self.offsets = OffsetTracker()
        self.acked: list[tuple[Delivery, bool]] = []

    def put_raw(self, body: bytes) -> Delivery:
        """Enqueues a raw body; key and topic come from the envelope when it parses."""
        with self._lock:
            offset = self._next_offset
            self._next_offset += 1
        try:
            delivery = delivery_from_envelope(Envelope.from_bytes(body), offset)
            delivery = Delivery(body, delivery.key, delivery.headers, delivery.topic, 0, offset)
        except ValueError:
            delivery = Delivery(body, None, {}, self._topic, 0, offset)
        self._queue.put(delivery)
        return delivery

    def events(self) -> Iterator[Delivery]:
        while not self._stop.is_set():
            try:
                delivery = self._queue.get(timeout=_POLL_SECONDS)
            except queue.Empty:
                if self._stop_when_drained:
                    return
                continue
            self.offsets.started((delivery.topic, delivery.partition), delivery.offset)
            yield delivery

    def ack(self, delivery: Delivery, processed: bool) -> None:
        with self._lock:
            self.acked.append((delivery, processed))
        if processed:
            self.offsets.done((delivery.topic, delivery.partition), delivery.offset)

    def request_stop(self) -> None:
        self._stop.set()

    def close(self) -> None:
        self._stop.set()

    def is_ready(self) -> bool:
        return True

    def lag(self) -> dict[PartitionId, int]:
        return {}


class InMemoryDlqPublisher:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.records: list[DlqRecord] = []
        self.fail_with: Exception | None = None  # tests: simulate a DLQ outage

    def publish(self, record: DlqRecord) -> None:
        if self.fail_with is not None:
            raise self.fail_with
        with self._lock:
            self.records.append(record)

    def close(self) -> None:
        return None
