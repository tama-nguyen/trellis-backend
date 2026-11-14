"""Backoff must match the Java implementation: both run the vectors in contracts/testdata."""

import json
from pathlib import Path
from typing import TypedDict

import pytest

from tests.support import CONTRACTS_DIR
from trellis_core.backoff import (
    DEFAULT_BASE_DELAY_MS,
    DEFAULT_MAX_ATTEMPTS,
    DEFAULT_MAX_DELAY_MS,
    DEFAULT_MULTIPLIER,
    Backoff,
)

VECTORS = CONTRACTS_DIR / "testdata" / "backoff.json"


class Case(TypedDict):
    attempt: int
    u: float
    ceilingMs: int
    delayMs: int


class ParameterSet(TypedDict):
    name: str
    baseDelayMs: int
    multiplier: float
    maxDelayMs: int
    maxAttempts: int
    cases: list[Case]


def _sets(path: Path) -> list[ParameterSet]:
    sets: list[ParameterSet] = json.loads(path.read_text(encoding="utf-8"))["parameterSets"]
    return sets


def test_python_defaults_are_the_documented_shared_defaults() -> None:
    defaults = next(s for s in _sets(VECTORS) if s["name"] == "defaults")
    assert (
        defaults["baseDelayMs"],
        defaults["multiplier"],
        defaults["maxDelayMs"],
        defaults["maxAttempts"],
    ) == (
        DEFAULT_BASE_DELAY_MS,
        DEFAULT_MULTIPLIER,
        DEFAULT_MAX_DELAY_MS,
        DEFAULT_MAX_ATTEMPTS,
    )


@pytest.mark.parametrize("params", _sets(VECTORS), ids=lambda p: p["name"])
def test_every_shared_vector_matches(params: ParameterSet) -> None:
    backoff = Backoff(
        base_delay_ms=params["baseDelayMs"],
        multiplier=params["multiplier"],
        max_delay_ms=params["maxDelayMs"],
        max_attempts=params["maxAttempts"],
    )
    mismatches = [
        (case, backoff.ceiling_ms(case["attempt"]), backoff.delay_ms(case["attempt"], case["u"]))
        for case in params["cases"]
        if (backoff.ceiling_ms(case["attempt"]), backoff.delay_ms(case["attempt"], case["u"]))
        != (case["ceilingMs"], case["delayMs"])
    ]
    assert mismatches == []
