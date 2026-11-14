"""Error hierarchy and process exit codes.

Workers need to know whether retrying can help, so every error that crosses a module boundary is
either :class:`TransientError` (retry with backoff) or :class:`PermanentError` (straight to the DLQ).
Anything else is treated as transient by the pipeline, because a wrong "permanent" loses an event
while a wrong "transient" only costs a few retries.
"""

from enum import IntEnum


class ExitCode(IntEnum):
    """Process exit codes. Values follow BSD sysexits so orchestrators and scripts can branch on them."""

    OK = 0
    FAILURE = 1  # runtime failure; the orchestrator should restart the process
    USAGE = 2  # bad command line (click's own code for usage errors)
    DATA_ERROR = 65  # input was read but is invalid (e.g. an envelope violates the contract)
    NO_INPUT = 66  # input file missing or unreadable
    UNAVAILABLE = 69  # a required service or plugin is unavailable
    TEMP_FAILURE = 75  # transient failure; retrying later may succeed
    CONFIG = 78  # configuration is invalid
    INTERRUPTED = 130  # stopped by a second SIGINT/SIGTERM before a clean shutdown


class TrellisError(Exception):
    """Base for every error Trellis raises on purpose."""

    exit_code: ExitCode = ExitCode.FAILURE
    #: Expected errors (bad config, invalid input) print one line; unexpected ones print a stack.
    show_stack_trace: bool = True


class ConfigurationError(TrellisError):
    """Configuration is missing or invalid. Never retried."""

    exit_code = ExitCode.CONFIG
    show_stack_trace = False


class UnboundVariableError(ConfigurationError):
    """A ``${VAR}`` reference in a config file names a variable that is not set."""


class InputError(TrellisError):
    """User-supplied input (a file, an event) is invalid."""

    exit_code = ExitCode.DATA_ERROR
    show_stack_trace = False


class TransientError(TrellisError):
    """Retrying later may succeed: timeouts, 503, lock conflicts, a broker that is briefly down."""

    exit_code = ExitCode.TEMP_FAILURE


class PermanentError(TrellisError):
    """Retrying cannot succeed: invalid payload, unknown event version, a 4xx that is not 408/429.

    The pipeline skips the remaining retries and sends the event to the DLQ immediately.
    """

    show_stack_trace = False


class PluginError(TrellisError):
    """A plugin could not be found, imported or validated."""

    exit_code = ExitCode.UNAVAILABLE
    show_stack_trace = False


def should_show_stack_trace(exc: BaseException) -> bool:
    """Whether the CLI prints a stack trace (unexpected bug) or one red line (expected failure)."""
    if isinstance(exc, TrellisError):
        return exc.show_stack_trace
    return True


def exit_code_for(exc: BaseException) -> ExitCode:
    """Maps an exception that escaped a command to the process exit code."""
    if isinstance(exc, TrellisError):
        return exc.exit_code
    if isinstance(exc, KeyboardInterrupt):
        return ExitCode.INTERRUPTED
    return ExitCode.FAILURE
