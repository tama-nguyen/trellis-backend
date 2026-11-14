#!/bin/sh
# Entry point for the local stack helper; the logic is in trellis_dev.py (stdlib-only Python 3.9+).
# Usage: scripts/dev/trellis-dev.sh --help
set -eu
here=$(cd "$(dirname "$0")" && pwd)
command -v python3 >/dev/null 2>&1 || { echo "trellis-dev: python3 is required" >&2; exit 2; }
command -v docker >/dev/null 2>&1 || { echo "trellis-dev: docker is required" >&2; exit 2; }
exec python3 "$here/trellis_dev.py" "$@"
