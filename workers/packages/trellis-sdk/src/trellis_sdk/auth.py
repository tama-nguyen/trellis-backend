"""Bearer tokens for the SDK. Workers live longer than tokens, so tokens are fetched per request.

``TokenProvider.get_token`` is called before every request; :class:`CachingTokenProvider` makes
that cheap and refreshes ``refresh_before`` ahead of expiry, so a request never leaves with a token
that expires in flight. On a 401 the client calls :meth:`TokenProvider.invalidate` and retries once.
"""

import threading
from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Protocol

from pydantic import SecretStr

from trellis_core.secrets import SecretRegistry

DEFAULT_REFRESH_BEFORE = timedelta(minutes=5)


@dataclass(frozen=True)
class TokenResult:
    token: SecretStr
    expires_at: datetime | None = None  # None = does not expire (personal access token)


class TokenProvider(Protocol):
    def get_token(self) -> TokenResult: ...

    def invalidate(self) -> None:
        """Forget any cached token (after a 401)."""
        ...


class StaticTokenProvider:
    """A personal access token from config or a profile."""

    def __init__(self, token: SecretStr) -> None:
        SecretRegistry.default().register("api token", token)
        self._result = TokenResult(token)

    def get_token(self) -> TokenResult:
        return self._result

    def invalidate(self) -> None:
        return None


class CachingTokenProvider:
    """Wraps a fetching function (OIDC client credentials, k8s service account, ...)."""

    def __init__(
        self,
        fetch: Callable[[], TokenResult],
        refresh_before: timedelta = DEFAULT_REFRESH_BEFORE,
        clock: Callable[[], datetime] = lambda: datetime.now(UTC),
    ) -> None:
        self._fetch = fetch
        self._refresh_before = refresh_before
        self._clock = clock
        self._lock = threading.Lock()
        self._current: TokenResult | None = None

    def get_token(self) -> TokenResult:
        with self._lock:
            if self._current is None or self._expiring(self._current):
                self._current = self._fetch()
                SecretRegistry.default().register("api token", self._current.token)
            return self._current

    def invalidate(self) -> None:
        with self._lock:
            self._current = None

    def _expiring(self, result: TokenResult) -> bool:
        if result.expires_at is None:
            return False
        return self._clock() >= result.expires_at - self._refresh_before
