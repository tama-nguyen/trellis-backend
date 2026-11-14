"""Process-wide registry of secret values and the log masking built on it.

Secrets arrive from two places: ``SecretStr`` fields of any :class:`~trellis_core.config.ConfigModel`
and every ``${VAR}`` a config file expands. Both register here, and every log line (structlog and the
stdlib ``logging`` bridge) is passed through :meth:`SecretRegistry.mask`.

Masking fails closed: if masking itself raises while secrets are registered, the whole line is
replaced by :data:`MASKING_FAILED_MARKER` instead of being written unmasked.
"""

import logging
import threading
from typing import ClassVar

from pydantic import SecretBytes, SecretStr

MASK = "********"
MASKING_FAILED_MARKER = "[log line withheld: secret masking failed]"
# Very short values ("1", "on") would mask half of every log line and protect nothing.
MIN_SECRET_LENGTH = 4


class SecretRegistry:
    """Thread-safe set of secret values. Use :meth:`default` for the process-wide instance."""

    _default: ClassVar["SecretRegistry | None"] = None
    _default_lock: ClassVar[threading.Lock] = threading.Lock()

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._values: dict[str, str] = {}  # secret value -> name (name is for diagnostics only)
        self._ordered: tuple[str, ...] = ()  # longest first, so a secret containing another wins
        self.enabled = True

    @classmethod
    def default(cls) -> "SecretRegistry":
        with cls._default_lock:
            if cls._default is None:
                cls._default = SecretRegistry()
            return cls._default

    def register(self, name: str, value: str | SecretStr | SecretBytes | None) -> None:
        if value is None:
            return
        raw: str
        if isinstance(value, SecretStr):
            raw = value.get_secret_value()
        elif isinstance(value, SecretBytes):
            raw = value.get_secret_value().decode("utf-8", errors="replace")
        else:
            raw = value
        if len(raw) < MIN_SECRET_LENGTH:
            return
        with self._lock:
            if raw in self._values:
                return
            self._values[raw] = name
            self._ordered = tuple(sorted(self._values, key=len, reverse=True))

    def names(self) -> list[str]:
        with self._lock:
            return sorted(set(self._values.values()))

    def clear(self) -> None:
        """For tests only."""
        with self._lock:
            self._values.clear()
            self._ordered = ()

    def has_secrets(self) -> bool:
        return bool(self._ordered)

    def mask(self, text: str) -> str:
        """Replaces every registered secret in ``text``. Idempotent: masking twice changes nothing."""
        if not self.enabled:
            return text
        ordered = self._ordered  # tuple swap is atomic; no lock on the hot path
        for secret in ordered:
            if secret in text:
                text = text.replace(secret, MASK)
        return text

    def mask_or_withhold(self, text: str) -> str:
        """:meth:`mask`, but returns a fixed marker instead of raising (fail closed)."""
        try:
            return self.mask(text)
        except Exception:  # noqa: BLE001 - any failure must withhold the line, never leak it
            return MASKING_FAILED_MARKER if self.has_secrets() else text


class SecretMaskingFilter(logging.Filter):
    """``logging`` filter for extra handlers with a plain (non-structlog) formatter, e.g. a file.

    The default handler installed by ``configure_logging`` does not need it: there the whole
    rendered line is masked. Do not attach it to a ``structlog`` ``ProcessorFormatter`` handler,
    whose records carry a dict, not a string.
    """

    def __init__(self, registry: SecretRegistry | None = None) -> None:
        super().__init__()
        self._registry = registry or SecretRegistry.default()

    def filter(self, record: logging.LogRecord) -> bool:
        try:
            message = record.getMessage()
        except Exception:  # noqa: BLE001 - a broken %-format must not leak its args either
            message = MASKING_FAILED_MARKER
        record.msg = self._registry.mask_or_withhold(message)
        record.args = None
        if record.exc_text:
            record.exc_text = self._registry.mask_or_withhold(record.exc_text)
        return True
