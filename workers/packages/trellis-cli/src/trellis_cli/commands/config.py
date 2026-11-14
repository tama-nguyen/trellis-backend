"""``trellis config show``: the effective settings and profile, secrets redacted."""

import click

from trellis_cli.output import CliState, announce, emit
from trellis_cli.profiles import resolve_profile
from trellis_core.config import redacted_dump
from trellis_core.env_vars import get_settings
from trellis_core.errors import ExitCode
from trellis_core.json_types import JsonObject


@click.group("config")
def config_group() -> None:
    """Inspect configuration."""


@config_group.command("show")
@click.pass_obj
def show_command(state: CliState) -> int:
    """Print TRELLIS_* settings and the active profile. Secrets are shown as ********."""
    settings = get_settings()
    env = redacted_dump(settings)
    resolved = resolve_profile(state.profile)
    for warning in resolved.warnings:
        announce(f"warning: {warning}")
    data: JsonObject = {
        "settings": env,
        "profile": {
            "name": resolved.name,
            "source": resolved.source,
            "file": str(resolved.path),
            "values": resolved.profile.model_dump_redacted(),
        },
    }
    lines = [f"{k} = {v}" for k, v in env.items()]
    lines.append(f"profile = {resolved.name} (from {resolved.source}, {resolved.path})")
    emit(state, data, "\n".join(lines))
    return ExitCode.OK
