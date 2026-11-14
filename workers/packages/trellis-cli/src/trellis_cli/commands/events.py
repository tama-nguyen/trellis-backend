"""``trellis events validate``: check envelope files against ``contracts/events``."""

import json
from pathlib import Path

import click

from trellis_cli.output import CliState, emit
from trellis_core.errors import ExitCode
from trellis_core.json_types import JsonObject, JsonValue
from trellis_events.contract_validation import ContractValidator, find_contracts_dir


@click.group("events")
def events_group() -> None:
    """Work with event envelopes and their contracts."""


@events_group.command("validate")
@click.argument("files", nargs=-1, required=True, type=click.Path(path_type=Path, dir_okay=False))
@click.option(
    "--contracts-dir",
    type=click.Path(path_type=Path, file_okay=False, exists=True),
    help="The repo's contracts/ folder (default: TRELLIS_CONTRACTS_DIR, then search upwards).",
)
@click.pass_obj
def validate_command(state: CliState, files: tuple[Path, ...], contracts_dir: Path | None) -> int:
    """Validate envelope JSON FILES against the envelope schema and their payload schema.

    Exit 0 when every file is valid, 65 when any is invalid, 66 when a file cannot be read.
    """
    validator = ContractValidator(contracts_dir or find_contracts_dir())
    results: list[JsonValue] = []
    lines: list[str] = []
    exit_code = ExitCode.OK
    for path in files:
        result: JsonObject = {"file": str(path)}
        try:
            issues = validator.validate_file(path)
        except OSError as exc:
            result.update(valid=False, error=f"cannot read: {exc.strerror}")
            lines.append(f"MISSING {path}: {exc.strerror}")
            exit_code = max(exit_code, ExitCode.NO_INPUT)
        except (json.JSONDecodeError, TypeError) as exc:
            result.update(valid=False, error=f"not JSON: {exc}")
            lines.append(f"INVALID {path}: not JSON ({exc})")
            exit_code = max(exit_code, ExitCode.DATA_ERROR)
        else:
            result["valid"] = not issues
            result["issues"] = [
                {"location": i.location, "message": i.message, "schema": i.schema} for i in issues
            ]
            if issues:
                exit_code = max(exit_code, ExitCode.DATA_ERROR)
                lines.append(f"INVALID {path}")
                lines.extend(f"  {i.location}: {i.message}" for i in issues)
            else:
                lines.append(f"VALID   {path}")
        results.append(result)
    emit(state, {"results": results, "valid": exit_code == ExitCode.OK}, "\n".join(lines))
    return exit_code
