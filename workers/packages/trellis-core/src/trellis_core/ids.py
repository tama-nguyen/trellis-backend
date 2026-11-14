"""Identifier helpers shared with Java: UUIDv7 for new ids, StableId for ids derived from content.

* :func:`uuid7` (RFC 9562): time-ordered, so B-tree indexes stay compact and ids double as the keyset
  tie-breaker. Python 3.12 has no ``uuid.uuid7``; this one is monotonic within a process.
* :func:`stable_id`: the same inputs give the same UUID in Java and Python. Algorithm::

      canonical = JSON with sorted keys, no whitespace, UTF-8, strings NFC-normalised
      digest    = SHA-256( utf8(namespace) || 0x00 || canonical )
      uuid      = digest[0:16] with version nibble 8 and the RFC 4122 variant

  Floats are rejected: Java and Python print them differently, so a float would silently break
  cross-language equality. Shared vectors: ``contracts/testdata/stable-id.json``.
"""

import hashlib
import json
import os
import threading
import time
import unicodedata
import uuid
from collections.abc import Mapping, Sequence

from trellis_core.json_types import JsonValue

_lock = threading.Lock()
_last_v7 = 0


def uuid7(now_ms: int | None = None) -> uuid.UUID:
    """A new UUIDv7. ``now_ms`` (Unix epoch milliseconds) is for tests and back-filling."""
    global _last_v7
    timestamp = time.time_ns() // 1_000_000 if now_ms is None else now_ms
    # "Logical" value: 48-bit ms timestamp then 74 random bits (rand_a 12 + rand_b 62). Monotonicity
    # is kept in this space, before the fixed version/variant bits are spliced in.
    logical = (timestamp & 0xFFFF_FFFF_FFFF) << 74 | int.from_bytes(os.urandom(10), "big") >> 6
    if now_ms is None:
        with _lock:
            # Same millisecond or a clock step back: continue after the previous id so ids sort.
            if logical <= _last_v7:
                logical = _last_v7 + 1
            _last_v7 = logical
    ts = logical >> 74
    rand_a = (logical >> 62) & 0xFFF
    rand_b = logical & ((1 << 62) - 1)
    value = ts << 80 | 0x7 << 76 | rand_a << 64 | 0x2 << 62 | rand_b
    return uuid.UUID(int=value)


def canonical_json(value: JsonValue) -> str:
    """Deterministic JSON text: sorted keys, no whitespace, NFC strings, no floats."""
    return json.dumps(
        _canonicalize(value), sort_keys=True, separators=(",", ":"), ensure_ascii=False
    )


def _canonicalize(value: JsonValue) -> JsonValue:
    if isinstance(value, float):
        raise TypeError("stable ids do not accept floats; encode the number as a string")
    if isinstance(value, str):
        return unicodedata.normalize("NFC", value)
    if isinstance(value, Mapping):
        return {unicodedata.normalize("NFC", k): _canonicalize(v) for k, v in value.items()}
    if isinstance(value, Sequence):
        return [_canonicalize(v) for v in value]
    return value


def stable_id(namespace: str, fields: Mapping[str, JsonValue]) -> uuid.UUID:
    """A UUIDv8 derived from ``namespace`` and ``fields``; identical in Java and Python."""
    if not namespace:
        raise ValueError("namespace must not be empty")
    payload = canonical_json(dict(fields)).encode("utf-8")
    digest = bytearray(hashlib.sha256(namespace.encode("utf-8") + b"\x00" + payload).digest()[:16])
    digest[6] = (digest[6] & 0x0F) | 0x80  # version 8
    digest[8] = (digest[8] & 0x3F) | 0x80  # RFC 4122 variant
    return uuid.UUID(bytes=bytes(digest))
