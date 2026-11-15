#!/bin/sh
# Entry point for the new-module generator; the logic is in new_module.py (stdlib-only Python 3.9+).
# Usage: build-logic/scripts/dev/new-module.sh --help
set -eu
here=$(cd "$(dirname "$0")" && pwd)
command -v python3 >/dev/null 2>&1 || { echo "new-module: python3 is required" >&2; exit 2; }
exec python3 "$here/new_module.py" "$@"
