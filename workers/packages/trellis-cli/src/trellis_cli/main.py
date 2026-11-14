"""Entry point of the ``trellis`` command.

``main()`` runs click with ``standalone_mode=False`` so that *we* map every outcome to an exit code
(click would otherwise call ``sys.exit`` itself and swallow exceptions):

* a command returns an ``ExitCode``                       -> that code
* ``click.UsageError`` and friends                        -> click's code (2), message on stderr
* :class:`~trellis_core.errors.TrellisError` (expected)    -> its code, one line on stderr
* pydantic ``ValidationError`` (bad config/input)         -> 78, the error list on stderr
* anything else (a bug)                                   -> 1, stack trace in the log

Every command is wrapped with ``@instrumented`` by :func:`_instrument_all`, so a new command cannot
forget tracing.
"""

import sys
from collections.abc import Sequence

import click
from pydantic import ValidationError

from trellis_cli.agent_help import AgentAwareGroup
from trellis_cli.commands.config import config_group
from trellis_cli.commands.events import events_group
from trellis_cli.commands.version import version_command
from trellis_cli.commands.worker import worker_group
from trellis_cli.output import CliState, OutputFormat
from trellis_core.errors import ExitCode, TrellisError, exit_code_for, should_show_stack_trace
from trellis_core.instrumentation import instrumented, is_instrumented
from trellis_core.logs import configure_logging, get_logger
from trellis_core.tracing import configure_tracing, shutdown_tracing

_log = get_logger(__name__)


@click.group(cls=AgentAwareGroup, context_settings={"help_option_names": ["-h", "--help"]})
@click.option(
    "--format",
    "output",
    type=click.Choice([f.value for f in OutputFormat]),
    default=OutputFormat.TEXT.value,
    show_default=True,
    help="Output format of the command result (stdout).",
)
@click.option("--profile", help="Backend profile from profiles.toml (default: TRELLIS_PROFILE).")
@click.option("--log-level", help="Override TRELLIS_LOG_LEVEL (DEBUG, INFO, WARNING, ERROR).")
@click.pass_context
def cli(ctx: click.Context, output: str, profile: str | None, log_level: str | None) -> None:
    """Trellis command line: validate events, run workers, inspect configuration."""
    configure_logging(level=log_level)
    configure_tracing()
    ctx.obj = CliState(output=OutputFormat(output), profile=profile)
    ctx.call_on_close(shutdown_tracing)


cli.add_command(version_command)
cli.add_command(events_group)
cli.add_command(worker_group)
cli.add_command(config_group)


def _instrument_all(group: click.Group, prefix: str = "cli") -> None:
    for name, command in group.commands.items():
        path = f"{prefix}.{name}"
        if isinstance(command, click.Group):
            _instrument_all(command, path)
        elif command.callback is not None and not is_instrumented(command.callback):
            command.callback = instrumented(path)(command.callback)


_instrument_all(cli)


def main(argv: Sequence[str] | None = None) -> int:
    try:
        result = cli.main(
            args=list(argv) if argv is not None else None,
            prog_name="trellis",
            standalone_mode=False,
        )
    except click.exceptions.Exit as exc:
        return exc.exit_code
    except click.ClickException as exc:
        exc.show()
        return exc.exit_code
    except (click.Abort, KeyboardInterrupt):
        click.echo("interrupted", err=True)
        return ExitCode.INTERRUPTED
    except ValidationError as exc:
        click.secho(f"invalid configuration: {exc}", fg="red", err=True)
        return ExitCode.CONFIG
    except TrellisError as exc:
        if should_show_stack_trace(exc):
            _log.exception("command failed")
        click.secho(f"error: {exc}", fg="red", err=True)
        return exit_code_for(exc)
    except Exception as exc:  # noqa: BLE001 - last resort: map any bug to exit 1
        _log.exception("command failed with an unexpected error")
        click.secho(f"unexpected error: {type(exc).__name__}: {exc}", fg="red", err=True)
        return ExitCode.FAILURE
    return int(result) if isinstance(result, int) else ExitCode.OK


def run() -> None:
    """Console-script entry point."""
    sys.exit(main())
