"""What a worker author writes: a :class:`WorkerDefinition` plugin that returns the pipeline parts.

The framework owns the source, the DLQ, retries, acknowledgement and shutdown; a worker only says
which event types it consumes and supplies the filters, transformers and the action::

    @plugin(name="notice-stats", description="...", config=NoticeStatsConfig)
    class NoticeStatsWorker(WorkerDefinition):
        def build(self, worker_config, ctx):
            config = NoticeStatsConfig.model_validate(worker_config)
            return WorkerParts(event_types=frozenset({"notice.published"}), action=...)

Register it as an entry point in the ``trellis.workers`` group of the worker's ``pyproject.toml``.
"""

from abc import ABC, abstractmethod
from collections.abc import Callable, Sequence
from dataclasses import dataclass, field

from pydantic import SecretStr

from trellis_core.json_types import JsonObject
from trellis_core.plugins import PluginRegistry
from trellis_events.constants import WORKER_ENTRY_POINT_GROUP
from trellis_events.payloads.registry import PayloadRegistry
from trellis_events.pipeline.roles import Action, Filter, Transformer
from trellis_events.pipeline.settings import PipelineReport, PipelineSettings


@dataclass(frozen=True)
class WorkerContext:
    settings: PipelineSettings
    report: PipelineReport
    payloads: PayloadRegistry
    #: ``TRELLIS_DATABASE_URL``: the default database for workers that keep state (inbox, tables).
    database_url: SecretStr | None = None


def _nothing() -> None:
    return None


def _always_ready() -> bool:
    return True


def _empty_status() -> JsonObject:
    return {}


@dataclass(frozen=True)
class WorkerParts:
    event_types: frozenset[str]
    action: Action
    filters: Sequence[Filter] = ()
    transformers: Sequence[Transformer] = ()
    #: Worker-specific numbers for ``--format json`` output and the status endpoint.
    status: Callable[[], JsonObject] = field(default=_empty_status)
    #: Readiness of the worker's own dependencies (e.g. the inbox database) for /health/ready.
    ready: Callable[[], bool] = field(default=_always_ready)
    #: Releases worker resources (connection pools); called once after the pipeline closed.
    close: Callable[[], None] = field(default=_nothing)


class WorkerDefinition(ABC):
    @abstractmethod
    def build(self, worker_config: JsonObject, ctx: WorkerContext) -> WorkerParts:
        """Validates ``worker_config`` with the worker's own ConfigModel and wires the parts."""


def worker_registry() -> PluginRegistry[WorkerDefinition]:
    """Workers installed in this environment, discovered from entry points."""
    return PluginRegistry[WorkerDefinition](
        WorkerDefinition,
        WORKER_ENTRY_POINT_GROUP,
        install_hint="uv sync --package trellis-{extra}",
    )
