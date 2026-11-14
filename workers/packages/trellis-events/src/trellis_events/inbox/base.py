"""The inbox (idempotent consumer) contract.

Delivery is at-least-once, so every consumer must make a redelivered event harmless. The inbox does
that with one row per ``(consumer_name, event_id)`` inserted **in the same transaction** as the
consumer's side effect::

    with inbox.transaction(event_id, correlation_id) as tx:
        if tx is None:
            return            # duplicate: already applied, acknowledge and move on
        store.apply(tx, ...)  # same transaction as the inbox row: both commit or neither does

Table ``processed_event`` is shared with the Java side (same columns, same key); its DDL lives in
:mod:`trellis_events.inbox.postgres`. A side effect outside the database (an email, an HTTP call)
cannot join the transaction: pass ``event_id`` as the remote idempotency key instead.
"""

import uuid
from contextlib import AbstractContextManager
from typing import Protocol


class InboxGuard[TxT](Protocol):
    consumer_name: str

    def transaction(
        self, event_id: uuid.UUID, correlation_id: str | None
    ) -> AbstractContextManager[TxT | None]:
        """Yields a transaction handle on first delivery, None for a duplicate.

        The inbox row commits only if the block exits normally; an exception rolls back both the
        row and the side effect, so the retry sees a first delivery again.
        """
        ...
