"""Assembles a runnable pipeline from a worker plugin and a config file.

Config file (``.toml`` or ``.json``; ``${VAR}`` expanded, unset variables are errors)::

    [pipeline]
    consumer_name = "notice-stats"
    failure_mode = "CONTINUE"        # or THROW
    [pipeline.retry]
    max_attempts = 4

    [transport]
    type = "kafka"                   # or "inmemory"
    bootstrap_servers = "${TRELLIS_KAFKA_BOOTSTRAP_SERVERS}"

    [worker]                         # validated by the worker's own ConfigModel
    store = "memory"
"""

import time
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Annotated, Literal

from pydantic import ConfigDict, Field

from trellis_core.config import ConfigModel
from trellis_core.env_vars import get_settings
from trellis_core.errors import ConfigurationError, ExitCode
from trellis_core.json_types import JsonObject
from trellis_core.logs import get_logger
from trellis_core.plugins import get_plugin_meta
from trellis_core.secrets import MASK
from trellis_events.health import HealthConfig, HealthServer
from trellis_events.payloads.registry import default_payload_registry
from trellis_events.pipeline.engine import Pipeline
from trellis_events.pipeline.roles import DlqPublisher, EventSource
from trellis_events.pipeline.settings import PipelineReport, PipelineSettings
from trellis_events.pipeline.shutdown import ShutdownSignal
from trellis_events.transports.memory import (
    InMemoryDlqPublisher,
    InMemorySource,
    InMemoryTransportConfig,
    read_raw_inputs,
)
from trellis_events.worker import WorkerContext, WorkerParts, worker_registry

_log = get_logger(__name__)

if TYPE_CHECKING:
    from trellis_events.transports.kafka import KafkaTransportConfig


class KafkaTransportRef(ConfigModel):
    """Kafka settings, validated lazily so the ``kafka`` extra is only needed when used."""

    model_config = ConfigDict(extra="allow")  # validated for real by KafkaTransportConfig
    type: Literal["kafka"] = "kafka"


class WorkerFileConfig(ConfigModel):
    pipeline: PipelineSettings
    transport: Annotated[
        InMemoryTransportConfig | KafkaTransportRef, Field(discriminator="type")
    ] = InMemoryTransportConfig()
    worker: JsonObject = {}
    health: HealthConfig = HealthConfig()


@dataclass
class RunnableWorker:
    name: str
    pipeline: Pipeline
    parts: WorkerParts
    #: In-memory transport only: lets callers inspect the DLQ after a run.
    memory_dlq: InMemoryDlqPublisher | None = None
    health_config: HealthConfig = field(default_factory=HealthConfig)
    #: Worker status captured right before its resources were closed.
    final_status: JsonObject = field(default_factory=dict)
    health_server: HealthServer | None = None

    def run(self) -> ExitCode:
        health = self._start_health()
        try:
            return self.pipeline.run()
        finally:
            if health is not None:
                if self.pipeline.stopping and self.health_config.shutdown_grace_s > 0:
                    # Readiness already answers 503 (stopping); give probes time to see it.
                    time.sleep(self.health_config.shutdown_grace_s)
                health.stop()
            # Snapshot before close(): the worker's resources (e.g. its pool) are gone afterwards.
            try:
                self.final_status = self.parts.status()
            except Exception:  # noqa: BLE001 - status is best effort; never mask the exit code
                _log.exception("worker status unavailable")
            self.parts.close()

    def _start_health(self) -> HealthServer | None:
        if not self.health_config.enabled:
            return None
        pipeline, parts = self.pipeline, self.parts
        server = HealthServer(
            self.health_config,
            consumer=pipeline.settings.consumer_name,
            checks={
                "source": pipeline.source.is_ready,
                "worker": parts.ready,
                "running": lambda: not pipeline.stopping,
            },
            counters=lambda: _int_counters(pipeline.report.as_obj()),
            lag=pipeline.source.lag,
        )
        server.start()
        self.health_server = server
        return server


def _int_counters(report: JsonObject) -> dict[str, int]:
    return {k: v for k, v in report.items() if isinstance(v, int) and not isinstance(v, bool)}


def build_worker(name: str, config: WorkerFileConfig, shutdown: ShutdownSignal) -> RunnableWorker:
    definition_cls = worker_registry().get(name)
    report = PipelineReport()
    payloads = default_payload_registry()
    ctx = WorkerContext(
        settings=config.pipeline,
        report=report,
        payloads=payloads,
        database_url=get_settings().database_url,
    )
    parts = definition_cls().build(config.worker, ctx)
    source, dlq, memory_dlq = _transport(config, parts)
    pipeline = Pipeline(
        settings=config.pipeline,
        source=source,
        action=parts.action,
        event_types=parts.event_types,
        payloads=payloads,
        dlq=dlq,
        filters=parts.filters,
        transformers=parts.transformers,
        shutdown=shutdown,
        report=report,
    )
    return RunnableWorker(name, pipeline, parts, memory_dlq, config.health)


def _kafka_config(transport: KafkaTransportRef) -> "KafkaTransportConfig":
    try:
        # Imported here so deployments without the ``kafka`` extra can still run in memory.
        from trellis_events.transports.kafka import KafkaTransportConfig
    except ModuleNotFoundError as exc:
        raise ConfigurationError(
            "transport 'kafka' needs the extra: install trellis-events[kafka]"
        ) from exc
    return KafkaTransportConfig.model_validate(transport.model_dump())


def describe_config(config: WorkerFileConfig, name: str) -> JsonObject:
    """Fully validated config (transport and worker sections too), secrets masked: for --dry-run."""
    transport: ConfigModel = (
        config.transport
        if isinstance(config.transport, InMemoryTransportConfig)
        else _kafka_config(config.transport)
    )
    meta = get_plugin_meta(worker_registry().get(name))
    worker: JsonObject = {}
    if meta is not None and meta.config is not None:
        worker = meta.config.model_validate(config.worker).model_dump_redacted()
    elif config.worker:
        worker = {key: MASK for key in config.worker}  # unknown schema: show keys only
    return {
        "pipeline": config.pipeline.model_dump_redacted(),
        "transport": transport.model_dump_redacted(),
        "worker": worker,
        "health": config.health.model_dump_redacted(),
    }


def _transport(
    config: WorkerFileConfig, parts: WorkerParts
) -> tuple[EventSource, DlqPublisher, InMemoryDlqPublisher | None]:
    transport = config.transport
    if isinstance(transport, InMemoryTransportConfig):
        source = InMemorySource(stop_when_drained=transport.drains())
        for body in read_raw_inputs(transport.inputs):
            source.put_raw(body)
        dlq = InMemoryDlqPublisher()
        return source, dlq, dlq
    kafka_config = _kafka_config(transport)
    from trellis_events.transports.kafka import KafkaDlqPublisher, KafkaSource

    topics = kafka_config.topics or sorted(parts.event_types)
    group = kafka_config.group_id or config.pipeline.consumer_name
    kafka_source = KafkaSource(kafka_config, group_id=group, topics=topics)
    return kafka_source, KafkaDlqPublisher(kafka_config), None
