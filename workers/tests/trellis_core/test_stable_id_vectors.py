"""StableId must match the Java implementation: both run the vectors in contracts/testdata."""

import json
from typing import TypedDict

import pytest

from tests.support import CONTRACTS_DIR
from trellis_core.ids import canonical_json, stable_id, uuid7
from trellis_core.json_types import JsonObject

VECTORS = CONTRACTS_DIR / "testdata" / "stable-id.json"


class Case(TypedDict):
    namespace: str
    fields: JsonObject
    canonicalJson: str
    uuid: str


def _cases() -> list[Case]:
    cases: list[Case] = json.loads(VECTORS.read_text(encoding="utf-8"))["cases"]
    return cases


@pytest.mark.parametrize("case", _cases(), ids=lambda c: c["uuid"])
def test_stable_id_matches_shared_vector(case: Case) -> None:
    assert canonical_json(case["fields"]) == case["canonicalJson"]
    assert str(stable_id(case["namespace"], case["fields"])) == case["uuid"]


def test_uuid7_is_version_7_and_sorts_by_creation() -> None:
    ids = [uuid7() for _ in range(1_000)]
    assert {u.version for u in ids} == {7}
    assert ids == sorted(ids)
