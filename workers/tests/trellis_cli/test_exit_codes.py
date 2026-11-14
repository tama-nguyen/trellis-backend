"""The CLI's exit codes are its contract with scripts, CI and orchestrators."""

import json
from pathlib import Path

import pytest

from tests.support import EXAMPLE_ENVELOPE, INVALID_ENVELOPE
from trellis_cli.main import main
from trellis_core.errors import ExitCode


def _json_out(capsys: pytest.CaptureFixture[str]) -> dict[str, object]:
    parsed: dict[str, object] = json.loads(capsys.readouterr().out)
    return parsed


def test_version_succeeds() -> None:
    assert main(["version"]) == ExitCode.OK


def test_unknown_option_is_a_usage_error() -> None:
    assert main(["--no-such-option"]) == ExitCode.USAGE


def test_valid_envelope_exits_zero() -> None:
    assert main(["events", "validate", str(EXAMPLE_ENVELOPE)]) == ExitCode.OK


def test_invalid_envelope_exits_data_error(capsys: pytest.CaptureFixture[str]) -> None:
    code = main(["--format", "json", "events", "validate", str(INVALID_ENVELOPE)])
    assert code == ExitCode.DATA_ERROR
    assert _json_out(capsys)["valid"] is False


def test_missing_input_exits_no_input(tmp_path: Path) -> None:
    assert main(["events", "validate", str(tmp_path / "absent.json")]) == ExitCode.NO_INPUT


def test_unknown_worker_is_unavailable() -> None:
    assert main(["worker", "run", "no-such-worker", "--dry-run"]) == ExitCode.UNAVAILABLE


def test_unset_config_variable_is_a_config_error(tmp_path: Path) -> None:
    config = tmp_path / "worker.toml"
    config.write_text('[pipeline]\nconsumer_name = "${TRELLIS_TEST_UNSET_VARIABLE}"\n')
    assert main(["worker", "run", "notice-stats", "--config", str(config), "--dry-run"]) == (
        ExitCode.CONFIG
    )


def test_invalid_config_is_a_config_error(tmp_path: Path) -> None:
    config = tmp_path / "worker.toml"
    config.write_text('[pipeline]\nconsumer_name = "Not Valid"\n')
    assert main(["worker", "run", "notice-stats", "--config", str(config)]) == ExitCode.CONFIG


def test_dry_run_consumes_nothing(capsys: pytest.CaptureFixture[str]) -> None:
    code = main(["--format", "json", "worker", "run", "notice-stats", "--dry-run"])
    assert code == ExitCode.OK
    assert _json_out(capsys)["dry_run"] is True


def test_duplicate_delivery_is_applied_once(capsys: pytest.CaptureFixture[str]) -> None:
    """At-least-once + inbox: the same event delivered twice changes the counters once."""
    example = str(EXAMPLE_ENVELOPE)
    code = main(
        [
            "--format",
            "json",
            "worker",
            "run",
            "notice-stats",
            "--input",
            example,
            "--input",
            example,
        ]
    )
    assert code == ExitCode.OK
    out = _json_out(capsys)
    assert out["status"] == {"counters": {"published.audience.ALL": 1, "published.total": 1}}
    report = out["report"]
    assert isinstance(report, dict)
    assert (report["received"], report["duplicates"], report["dead_lettered"]) == (2, 1, 0)


def test_invalid_event_is_dead_lettered_and_acknowledged(
    capsys: pytest.CaptureFixture[str],
) -> None:
    code = main(
        ["--format", "json", "worker", "run", "notice-stats", "--input", str(INVALID_ENVELOPE)]
    )
    assert code == ExitCode.OK
    out = _json_out(capsys)
    dlq = out["dlq"]
    assert isinstance(dlq, list)
    assert [record["topic"] for record in dlq] == ["inmemory.dlq"]


def test_dry_run_plan_never_prints_secrets(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    secret_dsn = "postgresql://app:dry-run-secret-pw@db.example.test/app"
    monkeypatch.setenv("TRELLIS_TEST_DSN", secret_dsn)
    config = tmp_path / "worker.toml"
    config.write_text(
        '[pipeline]\nconsumer_name = "notice-stats"\n'
        '[worker]\nstore = "postgres"\npostgres_dsn = "${TRELLIS_TEST_DSN}"\n'
    )
    code = main(
        ["--format", "json", "worker", "run", "notice-stats", "--config", str(config), "--dry-run"]
    )
    assert code == ExitCode.OK
    captured = capsys.readouterr()
    assert "dry-run-secret-pw" not in captured.out + captured.err


def test_health_of_a_worker_that_is_not_running_is_unhealthy() -> None:
    # Port 1 is never a Trellis health endpoint; exit must be 1 (Docker reserves 2).
    assert main(["worker", "health", "notice-stats", "--port", "1", "--timeout", "1"]) == (
        ExitCode.FAILURE
    )


def test_kafka_container_mode_needs_bootstrap_servers(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("TRELLIS_EVENTS_TRANSPORT", "kafka")
    assert main(["worker", "run", "notice-stats", "--dry-run"]) == ExitCode.CONFIG
