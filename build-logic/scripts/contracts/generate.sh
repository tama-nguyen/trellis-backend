#!/bin/sh
# Regenerates the published contract packages from contracts/ (decision T22).
#
#   build-logic/scripts/contracts/generate.sh                      regenerate both halves in place
#   build-logic/scripts/contracts/generate.sh --check              fail with a diff when the output is stale
#   build-logic/scripts/contracts/generate.sh --python             only the PyPI package (needs uv)
#   build-logic/scripts/contracts/generate.sh --typescript         only the npm package (needs node + pnpm)
#
# Python half: contracts/packages/python/src/trellis_contracts/{events,api}/
# TypeScript half: contracts/packages/npm/src/ + contracts/packages/npm/schema.graphqls
#
# Generator versions are pinned here (Python) and in contracts/codegen/typescript/package.json +
# pnpm-lock.yaml (TypeScript), so the same input always produces the same output. The CI jobs in
# build-logic/ci/contracts.yml run exactly this script; the split flags exist because no CI image has both
# toolchains.
set -eu

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
MODE=""
WANT_PYTHON=""
WANT_TYPESCRIPT=""
for arg in "$@"; do
  case "$arg" in
    --check) MODE="--check" ;;
    --python) WANT_PYTHON=yes ;;
    --typescript) WANT_TYPESCRIPT=yes ;;
    *) echo "usage: build-logic/scripts/contracts/generate.sh [--check] [--python] [--typescript]" >&2; exit 2 ;;
  esac
done
if [ -z "$WANT_PYTHON$WANT_TYPESCRIPT" ]; then
  WANT_PYTHON=yes
  WANT_TYPESCRIPT=yes
fi

# Pinned exactly: the generated models (and the drift check) depend on this output.
DATAMODEL_CODE_GENERATOR=0.96.0
RUFF=0.14.5
# A real venv, not `uv run --with`: the generators shell out to `python -m ruff`, which needs the
# ruff binary next to the interpreter. Build output, so it is gitignored and cheap to throw away.
VENV="${TRELLIS_CONTRACTS_VENV:-$ROOT/build/contracts-venv}"

if [ -n "$WANT_PYTHON" ]; then
  echo "== python contracts"
  uv venv --quiet --allow-existing --python 3.12 "$VENV"
  VIRTUAL_ENV="$VENV" uv pip install --quiet \
    "datamodel-code-generator==$DATAMODEL_CODE_GENERATOR" "ruff==$RUFF"
  "$VENV/bin/python" "$ROOT/contracts/codegen/python/generate.py" $MODE
  "$VENV/bin/python" "$ROOT/contracts/codegen/python/generate_sdk.py" $MODE
fi

if [ -n "$WANT_TYPESCRIPT" ]; then
  echo "== typescript contracts"
  cd "$ROOT/contracts/codegen/typescript"
  export COREPACK_ENABLE_DOWNLOAD_PROMPT=0
  corepack enable >/dev/null 2>&1 || true
  pnpm install --frozen-lockfile --prefer-offline
  node generate.mjs $MODE
fi
