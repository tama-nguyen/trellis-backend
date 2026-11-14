"""Typed errors mapped from the backend's RFC 9457 ``ProblemDetail`` responses.

The backend adds ``code`` (stable, machine-readable), ``correlationId`` and ``traceId`` to every
problem. Callers branch on the exception class or ``code``, never on ``detail`` text.
"""

from typing import Final

import httpx
from pydantic import BaseModel, ConfigDict, ValidationError
from pydantic.alias_generators import to_camel

from trellis_core.errors import ExitCode, PermanentError, TransientError, TrellisError

PROBLEM_CONTENT_TYPE: Final = "application/problem+json"


class ProblemDetail(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="ignore")

    type: str = "about:blank"
    title: str | None = None
    status: int | None = None
    detail: str | None = None
    instance: str | None = None
    code: str | None = None
    correlation_id: str | None = None
    trace_id: str | None = None


class ApiError(TrellisError):
    """An HTTP error response from the backend."""

    show_stack_trace = False

    def __init__(self, status: int, problem: ProblemDetail, method: str, url: str) -> None:
        self.status = status
        self.problem = problem
        self.method = method
        self.url = url
        summary = problem.detail or problem.title or f"HTTP {status}"
        refs = ", ".join(
            f"{name}={value}"
            for name, value in (
                ("code", problem.code),
                ("correlationId", problem.correlation_id),
                ("traceId", problem.trace_id),
            )
            if value
        )
        super().__init__(f"{method} {url} -> {status}: {summary}" + (f" ({refs})" if refs else ""))

    @property
    def code(self) -> str | None:
        return self.problem.code


class ClientError(ApiError, PermanentError):
    """4xx: the request is wrong; retrying it unchanged cannot help."""

    exit_code = ExitCode.FAILURE


class BadRequestError(ClientError): ...


class UnauthorizedError(ClientError): ...


class ForbiddenError(ClientError): ...


class NotFoundError(ClientError): ...


class ConflictError(ClientError):
    """409/412: optimistic-lock conflict or a uniqueness rule; re-read, then decide."""


class UnprocessableError(ClientError): ...


class RateLimitedError(ApiError, TransientError):
    """429 after the client's own retries ran out."""

    def __init__(
        self,
        status: int,
        problem: ProblemDetail,
        method: str,
        url: str,
        retry_after_s: float | None,
    ) -> None:
        super().__init__(status, problem, method, url)
        self.retry_after_s = retry_after_s


class ServerError(ApiError, TransientError):
    """5xx after the client's own retries ran out."""


class TransportError(TransientError):
    """No HTTP response at all: DNS, connect, TLS, timeout."""


_BY_STATUS: Final[dict[int, type[ClientError]]] = {
    400: BadRequestError,
    401: UnauthorizedError,
    403: ForbiddenError,
    404: NotFoundError,
    409: ConflictError,
    412: ConflictError,
    422: UnprocessableError,
}


def parse_problem(response: httpx.Response) -> ProblemDetail:
    try:
        return ProblemDetail.model_validate_json(response.content)
    except ValidationError:
        return ProblemDetail(status=response.status_code, title=response.reason_phrase or None)


def error_from_response(response: httpx.Response, retry_after_s: float | None = None) -> ApiError:
    problem = parse_problem(response)
    method, url = response.request.method, str(response.request.url.copy_with(query=None))
    status = response.status_code
    if status == httpx.codes.TOO_MANY_REQUESTS:
        return RateLimitedError(status, problem, method, url, retry_after_s)
    if status >= httpx.codes.INTERNAL_SERVER_ERROR:
        return ServerError(status, problem, method, url)
    return _BY_STATUS.get(status, ClientError)(status, problem, method, url)
