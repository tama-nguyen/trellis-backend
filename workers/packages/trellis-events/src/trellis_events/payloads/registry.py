"""Payload model registry keyed by ``(eventType, eventVersion)``.

An event whose type is known but whose version is not is a :class:`PermanentError`: guessing at a
newer payload is worse than dead-lettering it, and the DLQ can be replayed once the worker knows
the new version.
"""

import threading

from pydantic import BaseModel, ValidationError

from trellis_core.errors import PermanentError
from trellis_events.envelope import Envelope
from trellis_events.generated.index import PAYLOAD_MODELS


class UnknownEventVersionError(PermanentError):
    """The worker has no payload model for this ``(eventType, eventVersion)``."""


class InvalidPayloadError(PermanentError):
    """The payload does not match the contract of its ``(eventType, eventVersion)``."""


class PayloadRegistry:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._models: dict[tuple[str, int], type[BaseModel]] = {}

    def register(self, event_type: str, version: int, model: type[BaseModel]) -> None:
        with self._lock:
            key = (event_type, version)
            if key in self._models and self._models[key] is not model:
                raise ValueError(f"{event_type} v{version} is already registered")
            self._models[key] = model

    def knows(self, event_type: str) -> bool:
        with self._lock:
            return any(t == event_type for t, _ in self._models)

    def parse(self, envelope: Envelope) -> BaseModel:
        with self._lock:
            model = self._models.get((envelope.event_type, envelope.event_version))
        if model is None:
            raise UnknownEventVersionError(
                f"no payload model for {envelope.event_type} v{envelope.event_version}"
            )
        try:
            return model.model_validate(envelope.payload)
        except ValidationError as exc:
            raise InvalidPayloadError(
                f"{envelope.event_type} v{envelope.event_version} payload is invalid: "
                f"{exc.error_count()} error(s)"
            ) from exc


def default_payload_registry() -> PayloadRegistry:
    """Every contract under ``contracts/events`` (generated index, so none can be forgotten)."""
    registry = PayloadRegistry()
    for (event_type, version), model in PAYLOAD_MODELS.items():
        registry.register(event_type, version, model)
    return registry
