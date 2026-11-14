"""``@instrumented``: wrap a function in an OpenTelemetry span and a timing log line.

The Python counterpart of Micrometer's ``@Observed`` on the Java side. Three properties matter:

* ``ParamSpec`` keeps the wrapped signature, so mypy still checks every call site.
* A marker attribute (not the function name) detects an already-instrumented function. ``@wraps``
  copies ``__name__``, so name-based detection never matches and everything gets wrapped twice;
  DataHub hit exactly this bug with ``with_telemetry``.
* Outcomes are classified: ``SystemExit(0)`` is success, ``KeyboardInterrupt`` is "cancelled",
  anything else is "error" (recorded on the span, then re-raised unchanged).

The CLI applies it to every command automatically (``trellis_cli.main``), so new commands cannot
forget it.
"""

import time
from collections.abc import Callable
from functools import wraps
from typing import Final

from opentelemetry.trace import Status, StatusCode

from trellis_core.logs import get_logger
from trellis_core.tracing import get_tracer

INSTRUMENTED_ATTR: Final = "__trellis_instrumented__"
_log = get_logger(__name__)


class Outcome:
    COMPLETED: Final = "completed"
    CANCELLED: Final = "cancelled"
    ERROR: Final = "error"


def is_instrumented(func: object) -> bool:
    """True if ``func`` or anything it wraps (via ``__wrapped__``) carries the marker."""
    seen: set[int] = set()
    current: object = func
    while current is not None and id(current) not in seen:
        if getattr(current, INSTRUMENTED_ATTR, False) is True:
            return True
        seen.add(id(current))
        current = getattr(current, "__wrapped__", None)
    return False


def instrumented[**P, R](name: str | None = None) -> Callable[[Callable[P, R]], Callable[P, R]]:
    """Decorator factory: ``@instrumented()`` or ``@instrumented("worker.handle")``."""

    def decorate(func: Callable[P, R]) -> Callable[P, R]:
        if is_instrumented(func):
            return func
        span_name = name or f"{func.__module__}.{func.__qualname__}"
        tracer = get_tracer(func.__module__)

        @wraps(func)
        def wrapper(*args: P.args, **kwargs: P.kwargs) -> R:
            started = time.perf_counter()
            outcome = Outcome.COMPLETED
            with tracer.start_as_current_span(span_name) as span:
                try:
                    return func(*args, **kwargs)
                except SystemExit as exc:
                    if exc.code not in (0, None):
                        outcome = Outcome.ERROR
                    raise
                except KeyboardInterrupt:
                    outcome = Outcome.CANCELLED
                    raise
                except BaseException as exc:
                    outcome = Outcome.ERROR
                    span.record_exception(exc)
                    span.set_status(Status(StatusCode.ERROR, type(exc).__name__))
                    raise
                finally:
                    _log.debug(
                        "instrumented call finished",
                        operation=span_name,
                        outcome=outcome,
                        duration_ms=round((time.perf_counter() - started) * 1000, 3),
                    )

        setattr(wrapper, INSTRUMENTED_ATTR, True)
        return wrapper

    return decorate
