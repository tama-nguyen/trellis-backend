"""Command output: ``--format json`` for scripts and agents, ``text`` for humans. Always stdout.

Logs go to stderr (``trellis_core.logs``), so ``trellis ... --format json | jq`` always parses.
"""

import json
import sys
from dataclasses import dataclass
from enum import StrEnum

import click

from trellis_core.json_types import JsonValue
from trellis_core.secrets import SecretRegistry


class OutputFormat(StrEnum):
    TEXT = "text"
    JSON = "json"


@dataclass
class CliState:
    output: OutputFormat = OutputFormat.TEXT
    profile: str | None = None


def emit(state: CliState, data: JsonValue, text: str) -> None:
    """Writes the result. Registered secrets are masked here too: output is copied into tickets
    and CI logs as often as log lines are."""
    rendered = (
        json.dumps(data, indent=2, sort_keys=False, ensure_ascii=False)
        if state.output == OutputFormat.JSON
        else text
    )
    click.echo(SecretRegistry.default().mask_or_withhold(rendered))


def announce(message: str) -> None:
    """Side-effect banner on stderr, so it never corrupts JSON on stdout."""
    click.echo(SecretRegistry.default().mask_or_withhold(message), err=True)


def stdout_is_tty() -> bool:
    return sys.stdout.isatty()
