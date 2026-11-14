"""``trellis version``."""

import platform
from importlib.metadata import PackageNotFoundError, version
from typing import Final

import click

from trellis_cli.output import CliState, emit
from trellis_core.errors import ExitCode
from trellis_core.json_types import JsonObject

PACKAGES: Final = ("trellis-cli", "trellis-core", "trellis-events", "trellis-sdk")


def _version_of(package: str) -> str | None:
    try:
        return version(package)
    except PackageNotFoundError:
        return None


@click.command("version")
@click.pass_obj
def version_command(state: CliState) -> int:
    """Print the versions of the Trellis packages and of Python."""
    packages: JsonObject = {name: _version_of(name) for name in PACKAGES}
    data: JsonObject = {"packages": packages, "python": platform.python_version()}
    emit(state, data, f"trellis {packages['trellis-cli']} (python {data['python']})")
    return ExitCode.OK
