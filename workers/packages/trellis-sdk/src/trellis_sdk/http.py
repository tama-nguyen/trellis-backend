"""The SDK's HTTP layer: one pooled ``httpx.Client``, retries, 429, token refresh, tracing headers.

* One client per process, reused: connection pooling and keep-alive, no TLS handshake per call.
* Default timeouts everywhere (an HTTP call without a timeout is a hung worker waiting to happen).
* Retries use the shared :class:`~trellis_core.backoff.Backoff`. Retried: connection errors,
  timeouts, 502/503/504, and 429 honouring ``Retry-After``. Only idempotent methods are retried,
  unless the request carries an ``Idempotency-Key`` header.
* Every request sends ``x-correlation-id`` (from the log context) and ``traceparent``.
* A 401 invalidates the cached token and is retried once with a fresh one.
"""

import time
from collections.abc import Callable, Mapping
from datetime import UTC, datetime
from email.utils import parsedate_to_datetime
from typing import Final, Self

import httpx
import structlog
from pydantic import AnyHttpUrl

from trellis_core.backoff import Backoff, BackoffConfig
from trellis_core.config import ConfigModel
from trellis_core.json_types import JsonValue
from trellis_core.logs import LogKeys, get_logger
from trellis_core.tracing import TRACEPARENT_HEADER, current_traceparent
from trellis_sdk.auth import TokenProvider
from trellis_sdk.errors import TransportError, error_from_response

CORRELATION_HEADER: Final = "x-correlation-id"
IDEMPOTENCY_HEADER: Final = "Idempotency-Key"
RETRY_AFTER_HEADER: Final = "Retry-After"
IDEMPOTENT_METHODS: Final = frozenset({"GET", "HEAD", "OPTIONS", "PUT", "DELETE"})
RETRY_STATUSES: Final = frozenset({429, 502, 503, 504})
USER_AGENT: Final = "trellis-sdk"

_log = get_logger(__name__)


class HttpClientConfig(ConfigModel):
    base_url: AnyHttpUrl
    connect_timeout_s: float = 5.0
    read_timeout_s: float = 30.0
    retry: BackoffConfig = BackoffConfig(max_attempts=4)
    max_retry_after_s: float = 60.0
    """A ``Retry-After`` longer than this is not waited for; the 429 is raised instead."""
    max_connections: int = 20


class TrellisHttpClient:
    def __init__(
        self,
        config: HttpClientConfig,
        tokens: TokenProvider | None = None,
        *,
        transport: httpx.BaseTransport | None = None,
        sleep: Callable[[float], None] = time.sleep,
    ) -> None:
        self._config = config
        self._tokens = tokens
        self._backoff = Backoff.from_config(config.retry)
        self._sleep = sleep
        self._client = httpx.Client(
            base_url=str(config.base_url),
            timeout=httpx.Timeout(config.read_timeout_s, connect=config.connect_timeout_s),
            limits=httpx.Limits(max_connections=config.max_connections),
            headers={"User-Agent": USER_AGENT},
            transport=transport,
        )

    def __enter__(self) -> Self:
        return self

    def __exit__(self, *_: object) -> None:
        self.close()

    def close(self) -> None:
        self._client.close()

    def request(
        self,
        method: str,
        path: str,
        *,
        json: JsonValue = None,
        params: Mapping[str, str] | None = None,
        headers: Mapping[str, str] | None = None,
    ) -> httpx.Response:
        """Sends with retries; returns a 2xx/3xx response or raises a typed :class:`ApiError`."""
        method = method.upper()
        extra = dict(headers or {})
        retryable = method in IDEMPOTENT_METHODS or IDEMPOTENCY_HEADER in extra
        refreshed = False
        attempt = 0
        while True:
            attempt += 1
            try:
                response = self._client.request(
                    method, path, json=json, params=params, headers=self._headers(extra)
                )
            except httpx.TransportError as exc:
                if retryable and self._backoff.should_retry(attempt):
                    self._wait(attempt, None, f"{type(exc).__name__}")
                    continue
                raise TransportError(f"{method} {path}: {type(exc).__name__}: {exc}") from exc
            if response.status_code == httpx.codes.UNAUTHORIZED and self._tokens and not refreshed:
                self._tokens.invalidate()
                refreshed = True
                continue
            if response.status_code < httpx.codes.BAD_REQUEST:
                return response
            retry_after = _retry_after_seconds(response)
            if (
                response.status_code in RETRY_STATUSES
                and retryable
                and self._backoff.should_retry(attempt)
                and (retry_after is None or retry_after <= self._config.max_retry_after_s)
            ):
                self._wait(attempt, retry_after, f"HTTP {response.status_code}")
                continue
            raise error_from_response(response, retry_after)

    def get_json(self, path: str, params: Mapping[str, str] | None = None) -> JsonValue:
        payload: JsonValue = self.request("GET", path, params=params).json()
        return payload

    def _headers(self, extra: dict[str, str]) -> dict[str, str]:
        headers = dict(extra)
        context = structlog.contextvars.get_contextvars()
        correlation = context.get(LogKeys.CORRELATION_ID) or context.get(LogKeys.REQUEST_ID)
        if isinstance(correlation, str):
            headers.setdefault(CORRELATION_HEADER, correlation)
        traceparent = current_traceparent()
        if traceparent:
            headers.setdefault(TRACEPARENT_HEADER, traceparent)
        if self._tokens is not None:
            headers["Authorization"] = f"Bearer {self._tokens.get_token().token.get_secret_value()}"
        return headers

    def _wait(self, attempt: int, retry_after: float | None, reason: str) -> None:
        delay = (
            retry_after if retry_after is not None else self._backoff.delay_ms(attempt - 1) / 1000
        )
        _log.warning("http request will be retried", reason=reason, attempt=attempt, delay_s=delay)
        self._sleep(delay)


def _retry_after_seconds(response: httpx.Response) -> float | None:
    """``Retry-After`` as seconds; accepts delta-seconds and HTTP-date forms (RFC 9110 10.2.3)."""
    raw: str | None = response.headers.get(RETRY_AFTER_HEADER)
    if raw is None:
        return None
    try:
        return max(0.0, float(raw))
    except ValueError:
        pass
    try:
        when = parsedate_to_datetime(raw)
    except (TypeError, ValueError):
        return None
    if when.tzinfo is None:
        when = when.replace(tzinfo=UTC)
    return max(0.0, (when - datetime.now(UTC)).total_seconds())
