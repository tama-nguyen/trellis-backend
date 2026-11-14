"""Configuration of the notice-stats worker (the ``[worker]`` section of its config file)."""

from enum import StrEnum

from pydantic import SecretStr

from trellis_core.config import ConfigModel
from trellis_core.errors import ConfigurationError


class StoreKind(StrEnum):
    MEMORY = "memory"
    POSTGRES = "postgres"


class NoticeStatsConfig(ConfigModel):
    store: StoreKind | None = None
    """``memory`` or ``postgres``. Default: ``postgres`` when a database URL is available
    (``postgres_dsn`` or ``TRELLIS_DATABASE_URL``), else ``memory`` (state dies with the process)."""
    postgres_dsn: SecretStr | None = None
    """libpq connection string; default ``TRELLIS_DATABASE_URL``."""
    pool_size: int = 4

    def resolve(self, database_url: SecretStr | None) -> tuple[StoreKind, SecretStr | None]:
        """The store to use and its DSN, after applying the defaults above."""
        dsn = self.postgres_dsn or database_url
        store = self.store or (StoreKind.POSTGRES if dsn is not None else StoreKind.MEMORY)
        if store == StoreKind.POSTGRES and dsn is None:
            raise ConfigurationError("store 'postgres' needs postgres_dsn or TRELLIS_DATABASE_URL")
        return store, dsn
