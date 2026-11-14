"""OpenTelemetry setup and W3C ``traceparent`` helpers.

Tracing is **off by default** (``TRELLIS_OTEL_ENABLED=false``): the API's no-op tracer is used and no
data leaves the process. Trellis sends no product telemetry to anyone, ever; enabling OTel only
exports to the collector the operator configures (``OTEL_EXPORTER_OTLP_*``).

Consumers continue the producer's trace from the envelope's ``traceparent`` (it is in the payload as
well as the Kafka header, so it survives replays and the DLQ).
"""

import importlib
import re
import sys
from collections.abc import Callable
from dataclasses import dataclass
from typing import Final, cast

from opentelemetry import context as otel_context
from opentelemetry import trace
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import (
    BatchSpanProcessor,
    ConsoleSpanExporter,
    SimpleSpanProcessor,
    SpanExporter,
)
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator

from trellis_core.env_vars import get_settings
from trellis_core.errors import ConfigurationError

TRACEPARENT_HEADER: Final = "traceparent"
_TRACEPARENT = re.compile(r"^([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$")
_OTLP_MODULE = "opentelemetry.exporter.otlp.proto.http.trace_exporter"
_propagator = TraceContextTextMapPropagator()


@dataclass(frozen=True)
class TraceParent:
    trace_id: str
    span_id: str
    flags: str

    @staticmethod
    def parse(value: str | None) -> "TraceParent | None":
        if not value:
            return None
        match = _TRACEPARENT.match(value)
        if match is None or match.group(2) == "0" * 32:
            return None
        return TraceParent(trace_id=match.group(2), span_id=match.group(3), flags=match.group(4))


def configure_tracing(service_name: str | None = None) -> bool:
    """Installs a TracerProvider if enabled. Returns whether tracing is active."""
    settings = get_settings()
    if not settings.otel_enabled:
        return False
    provider = TracerProvider(
        resource=Resource.create(
            {
                "service.name": service_name or settings.service_name,
                "deployment.environment": settings.env,
            }
        )
    )
    exporter_name = settings.otel_exporter
    if exporter_name == "console":
        # stderr: stdout carries command output.
        provider.add_span_processor(SimpleSpanProcessor(ConsoleSpanExporter(out=sys.stderr)))
    elif exporter_name == "otlp":
        try:
            module = importlib.import_module(_OTLP_MODULE)
        except ModuleNotFoundError as exc:
            raise ConfigurationError(
                "TRELLIS_OTEL_EXPORTER=otlp needs the extra: uv sync --extra otlp"
            ) from exc
        factory = cast("Callable[[], SpanExporter]", module.OTLPSpanExporter)
        provider.add_span_processor(BatchSpanProcessor(factory()))
    elif exporter_name != "none":
        raise ConfigurationError(f"unknown TRELLIS_OTEL_EXPORTER {exporter_name!r}")
    trace.set_tracer_provider(provider)
    return True


def shutdown_tracing() -> None:
    provider = trace.get_tracer_provider()
    if isinstance(provider, TracerProvider):
        provider.shutdown()


def get_tracer(name: str) -> trace.Tracer:
    return trace.get_tracer(name)


def context_from_traceparent(traceparent: str | None) -> otel_context.Context | None:
    """An OTel context whose parent is the remote span in ``traceparent`` (None if absent/invalid)."""
    if TraceParent.parse(traceparent) is None:
        return None
    return _propagator.extract({TRACEPARENT_HEADER: traceparent or ""})


def current_traceparent() -> str | None:
    """The ``traceparent`` of the active span, for outgoing HTTP calls and DLQ records."""
    carrier: dict[str, str] = {}
    _propagator.inject(carrier)
    return carrier.get(TRACEPARENT_HEADER)
