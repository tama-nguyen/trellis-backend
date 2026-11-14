"""Agent-friendly help: when stdout is not a terminal, ``--help`` appends machine-oriented notes.

A coding agent reads help through a pipe; a human in a terminal does not need this block. The text
lives here (one place) rather than in each command's docstring.
"""

from typing import Final

import click

from trellis_cli.output import stdout_is_tty

AGENT_CONTEXT: Final = """\
Notes for scripts and agents:
  - Put --format json before the command (trellis --format json events validate f.json); the JSON
    result goes to stdout, logs go to stderr as JSON lines.
  - Exit codes: 0 ok, 1 failure, 2 usage, 65 invalid input, 66 missing input, 69 plugin
    unavailable, 75 temporary failure, 78 bad config, 130 interrupted.
  - Commands with side effects accept --dry-run: they validate and print the plan, change nothing.
  - Config files expand ${VAR}; an unset variable fails with exit 78 and names the variable.
  - Secrets never appear in output; `trellis config show` prints them as ********.
"""


def _append_agent_context(formatter: click.HelpFormatter) -> None:
    if not stdout_is_tty():
        formatter.write_paragraph()
        formatter.write(AGENT_CONTEXT)


class AgentAwareCommand(click.Command):
    def format_epilog(self, ctx: click.Context, formatter: click.HelpFormatter) -> None:
        super().format_epilog(ctx, formatter)
        _append_agent_context(formatter)


class AgentAwareGroup(click.Group):
    command_class = AgentAwareCommand
    group_class = type  # subgroups use the same class as their parent

    def format_epilog(self, ctx: click.Context, formatter: click.HelpFormatter) -> None:
        super().format_epilog(ctx, formatter)
        _append_agent_context(formatter)
