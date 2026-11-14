"""The one seam between the generated resource classes and a real HTTP stack.

A consumer (trellis-ai's ``trellis_sdk``, a script, a test double) implements ``HttpTransport`` over
whatever it already uses - httpx, requests, a recording fake - and keeps ownership of retries, 429
handling, token refresh, correlation headers and ProblemDetail mapping. Raising on a non-2xx status
is the transport's job; the generated methods assume they are handed a successful response.
"""

from collections.abc import Mapping
from typing import Protocol, runtime_checkable


@runtime_checkable
class HttpResponse(Protocol):
    """Just enough of a response for the generated code: the raw body it validates models from."""

    @property
    def content(self) -> bytes: ...


@runtime_checkable
class HttpTransport(Protocol):
    """Performs one request against the backend and returns a successful response."""

    def request(
        self,
        method: str,
        path: str,
        *,
        params: Mapping[str, str] | None = None,
        headers: Mapping[str, str] | None = None,
        json: object | None = None,
    ) -> HttpResponse: ...
