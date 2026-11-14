"""The action: count each published notice exactly once, whatever the delivery count."""

from typing import Final

from trellis_core.errors import PermanentError
from trellis_core.logs import get_logger
from trellis_events.generated.notice_published_v1 import Audience, NoticePublishedV1
from trellis_events.inbox.base import InboxGuard
from trellis_events.pipeline.model import InboundEvent
from trellis_events.pipeline.settings import PipelineReport
from trellis_notice_stats.store import CounterStore

TOTAL_KEY: Final = "published.total"
AUDIENCE_KEY_PREFIX: Final = "published.audience."

_log = get_logger(__name__)


class CountPublishedNotices[TxT]:
    def __init__(
        self, inbox: InboxGuard[TxT], store: CounterStore[TxT], report: PipelineReport
    ) -> None:
        self._inbox = inbox
        self._store = store
        self._report = report

    def act(self, event: InboundEvent) -> None:
        payload = event.payload
        if not isinstance(payload, NoticePublishedV1):
            raise PermanentError(f"unexpected payload {type(payload).__name__}")
        envelope = event.envelope
        with self._inbox.transaction(envelope.event_id, envelope.correlation_id) as tx:
            if tx is None:
                self._report.increment("duplicates")
                _log.info("duplicate delivery skipped")
                return
            self._store.increment(tx, TOTAL_KEY)
            audience = payload.audience or Audience.ALL
            self._store.increment(tx, f"{AUDIENCE_KEY_PREFIX}{audience.value}")
