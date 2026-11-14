"""Paths shared by tests. Tests never depend on the working directory."""

from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
CONTRACTS_DIR = REPO_ROOT / "contracts"
EXAMPLE_ENVELOPE = CONTRACTS_DIR / "examples" / "notice.published.v1.json"
FIXTURES = Path(__file__).resolve().parent / "fixtures"
INVALID_ENVELOPE = FIXTURES / "invalid-notice.published.v1.json"
