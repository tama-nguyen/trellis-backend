#!/bin/sh
# Version for the published contract packages, derived from the git tag (decision T22).
#
#   build-logic/scripts/contracts/version.sh npm            print, e.g. 0.1.0 or 0.0.0-dev.312
#   build-logic/scripts/contracts/version.sh pypi --apply   print, and write it into the package metadata
#
# The committed metadata says 0.0.0: a contract change must never need a manual version bump in a
# merge request. The publish job stamps the real version just before building.
#
# Inputs (GitLab CI, both optional):
#   CI_COMMIT_TAG    contracts-vX.Y.Z or contracts-vX.Y.Z-rc.N  -> a contract release
#   CI_PIPELINE_IID  anything else                              -> a prerelease, unique per pipeline
#
# npm and PyPI spell prereleases differently (SemVer vs PEP 440), hence the two flavours:
#   contracts-v0.1.0       npm 0.1.0          pypi 0.1.0
#   contracts-v0.1.0-rc.2  npm 0.1.0-rc.2     pypi 0.1.0rc2
#   main, pipeline 312     npm 0.0.0-dev.312  pypi 0.0.0.dev312
#
# Written in POSIX sh, not Python: it runs in both the node and the uv CI image.
set -eu

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
FLAVOUR="${1:-}"
APPLY="${2:-}"
case "$FLAVOUR" in
  npm | pypi) ;;
  *) echo "usage: build-logic/scripts/contracts/version.sh <npm|pypi> [--apply]" >&2; exit 2 ;;
esac
[ -z "$APPLY" ] || [ "$APPLY" = "--apply" ] || {
  echo "usage: build-logic/scripts/contracts/version.sh <npm|pypi> [--apply]" >&2; exit 2
}

TAG="${CI_COMMIT_TAG:-}"
if [ -n "$TAG" ]; then
  RELEASE=$(printf '%s' "$TAG" | sed -n -E 's/^contracts-v([0-9]+\.[0-9]+\.[0-9]+)(-rc\.[0-9]+)?$/\1/p')
  RC=$(printf '%s' "$TAG" | sed -n -E 's/^contracts-v[0-9]+\.[0-9]+\.[0-9]+-rc\.([0-9]+)$/\1/p')
  if [ -z "$RELEASE" ]; then
    echo "$TAG: not a contract release tag (contracts-vX.Y.Z or contracts-vX.Y.Z-rc.N)" >&2
    exit 1
  fi
  if [ -z "$RC" ]; then
    VERSION="$RELEASE"
  elif [ "$FLAVOUR" = npm ]; then
    VERSION="$RELEASE-rc.$RC"
  else
    VERSION="${RELEASE}rc${RC}"
  fi
else
  IID="${CI_PIPELINE_IID:-0}"
  if [ "$FLAVOUR" = npm ]; then VERSION="0.0.0-dev.$IID"; else VERSION="0.0.0.dev$IID"; fi
fi

if [ "$APPLY" = "--apply" ]; then
  if [ "$FLAVOUR" = npm ]; then
    (cd "$ROOT/contracts/packages/npm" && npm pkg set version="$VERSION")
  else
    PYPROJECT="$ROOT/contracts/packages/python/pyproject.toml"
    TMP=$(mktemp)
    # Only the first `version = "..."` line, which is the project's own.
    awk -v v="$VERSION" '!done && /^version = /{print "version = \"" v "\""; done=1; next} {print}' \
      "$PYPROJECT" > "$TMP"
    mv "$TMP" "$PYPROJECT"
  fi
  echo "stamped $FLAVOUR version $VERSION" >&2
fi

printf '%s\n' "$VERSION"
