"""Exponential backoff with full jitter and a cap: the one retry-delay formula of Trellis.

Shared with the Java side; both implementations are checked against the same test vectors in
``contracts/testdata/backoff.json``. With ``attempt`` = 0 for the first retry, 1 for the second, ...::

    ceiling(attempt) = min(max_delay_ms, floor(base_delay_ms * multiplier ** attempt))
    delay(attempt)   = floor(u * ceiling(attempt))      with u ~ Uniform[0, 1)

Full jitter (``U(0, ceiling)``) rather than "equal" jitter (+-50%) because it spreads a herd of
consumers that failed together the widest. The cost is that a delay can be close to zero, which is
fine for consumers (the broker or the database is the bottleneck, not our retry rate).
"""

import math
import random
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import timedelta

from trellis_core.config import ConfigModel

#: Defaults shared with Java; change both or neither (the shared vector file pins them).
DEFAULT_BASE_DELAY_MS = 1_000
DEFAULT_MULTIPLIER = 2.0
DEFAULT_MAX_DELAY_MS = 30_000
#: Consumer default: the first attempt plus 3 retries, then the DLQ.
DEFAULT_MAX_ATTEMPTS = 4

# Clamp the exponent so a huge attempt number cannot overflow a float; the cap applies long before.
_MAX_EXPONENT = 62


class BackoffConfig(ConfigModel):
    base_delay_ms: int = DEFAULT_BASE_DELAY_MS
    multiplier: float = DEFAULT_MULTIPLIER
    max_delay_ms: int = DEFAULT_MAX_DELAY_MS
    max_attempts: int = DEFAULT_MAX_ATTEMPTS


@dataclass(frozen=True)
class Backoff:
    base_delay_ms: int = DEFAULT_BASE_DELAY_MS
    multiplier: float = DEFAULT_MULTIPLIER
    max_delay_ms: int = DEFAULT_MAX_DELAY_MS
    max_attempts: int = DEFAULT_MAX_ATTEMPTS
    #: Returns u in [0, 1). Injected so tests and the shared vectors are deterministic.
    random: Callable[[], float] = field(default=random.random, compare=False, repr=False)

    def __post_init__(self) -> None:
        if self.base_delay_ms <= 0 or self.max_delay_ms < self.base_delay_ms:
            raise ValueError("need 0 < base_delay_ms <= max_delay_ms")
        if self.multiplier < 1.0:
            raise ValueError("multiplier must be >= 1")
        if self.max_attempts < 1:
            raise ValueError("max_attempts must be >= 1")

    @classmethod
    def from_config(cls, config: BackoffConfig) -> "Backoff":
        return cls(
            base_delay_ms=config.base_delay_ms,
            multiplier=config.multiplier,
            max_delay_ms=config.max_delay_ms,
            max_attempts=config.max_attempts,
        )

    def ceiling_ms(self, attempt: int) -> int:
        if attempt < 0:
            raise ValueError("attempt must be >= 0")
        raw = self.base_delay_ms * self.multiplier ** min(attempt, _MAX_EXPONENT)
        return min(self.max_delay_ms, math.floor(raw))

    def delay_ms(self, attempt: int, u: float | None = None) -> int:
        """Delay before retry number ``attempt + 1``; ``u`` overrides the random draw (tests)."""
        draw = self.random() if u is None else u
        if not 0.0 <= draw < 1.0:
            raise ValueError("u must be in [0, 1)")
        return math.floor(draw * self.ceiling_ms(attempt))

    def delay(self, attempt: int) -> timedelta:
        return timedelta(milliseconds=self.delay_ms(attempt))

    def should_retry(self, attempts_made: int) -> bool:
        """Whether another attempt is allowed after ``attempts_made`` attempts have failed."""
        return attempts_made < self.max_attempts
