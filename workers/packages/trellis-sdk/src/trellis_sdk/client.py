"""``TrellisClient``: the SDK facade.

Typed operations come from the OpenAPI-generated client (``trellis_sdk.generated``, built from
``contracts/openapi/openapi.json``): one property per backend resource, e.g.
``client.notice.publish_notice(code, idempotency_key=key)``. Each resource calls
:attr:`TrellisClient.http`, so retries, 429, token refresh and typed ``ApiError``s apply. ``http`` stays
available for endpoints outside the contract. Never hand-write per-endpoint models here: they belong to
the generator's output.
"""

from typing import Self

from pydantic import SecretStr

from trellis_core.env_vars import get_settings
from trellis_core.errors import ConfigurationError
from trellis_sdk.auth import StaticTokenProvider, TokenProvider
from trellis_sdk.generated.resources import GeneratedResources
from trellis_sdk.http import HttpClientConfig, TrellisHttpClient


class TrellisClient(GeneratedResources):
    def __init__(self, config: HttpClientConfig, tokens: TokenProvider | None = None) -> None:
        self.http = TrellisHttpClient(config, tokens)

    @classmethod
    def from_env(cls) -> Self:
        """``TRELLIS_API_URL`` + optional ``TRELLIS_API_TOKEN``."""
        settings = get_settings()
        if not settings.api_url:
            raise ConfigurationError("TRELLIS_API_URL is not set")
        return cls.from_values(settings.api_url, settings.api_token)

    @classmethod
    def from_values(cls, api_url: str, token: SecretStr | None) -> Self:
        config = HttpClientConfig.model_validate({"base_url": api_url})
        return cls(config, StaticTokenProvider(token) if token is not None else None)

    def __enter__(self) -> Self:
        return self

    def __exit__(self, *_: object) -> None:
        self.close()

    def close(self) -> None:
        self.http.close()
