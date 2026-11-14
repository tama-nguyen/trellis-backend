"""Batching helpers for bulk calls: split by count and by serialized size.

Every chunk holds at least one item, so a single item larger than ``max_bytes`` still goes out
alone (and the server decides) instead of looping forever.
"""

from collections.abc import Callable, Iterable, Iterator


def chunked[T](items: Iterable[T], size: int) -> Iterator[list[T]]:
    if size < 1:
        raise ValueError("size must be >= 1")
    chunk: list[T] = []
    for item in items:
        chunk.append(item)
        if len(chunk) == size:
            yield chunk
            chunk = []
    if chunk:
        yield chunk


def chunked_by_bytes[T](
    items: Iterable[T], *, max_bytes: int, max_items: int, size_of: Callable[[T], int]
) -> Iterator[list[T]]:
    if max_bytes < 1 or max_items < 1:
        raise ValueError("max_bytes and max_items must be >= 1")
    chunk: list[T] = []
    used = 0
    for item in items:
        size = size_of(item)
        if chunk and (used + size > max_bytes or len(chunk) >= max_items):
            yield chunk
            chunk, used = [], 0
        chunk.append(item)
        used += size
    if chunk:
        yield chunk
