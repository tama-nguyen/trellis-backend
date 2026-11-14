#!/bin/sh
# Prints TRELLIS_<SVC>_VERSION=<tag> lines for build-logic/docker/compose.yml (smoke:compose, build-logic/ci/smoke.yml).
#
# A service rebuilt in this pipeline uses $IMAGE_TAG. A service skipped by the MR path rules uses the
# floating `main` tag, i.e. the last image that passed smoke on main (DataHub resolve_image_builds.sh).
# Fails if neither exists, so a smoke run never silently tests nothing.
#
# Needs: docker CLI logged in to $CI_REGISTRY, CI_REGISTRY_IMAGE, IMAGE_TAG.
set -eu

: "${CI_REGISTRY_IMAGE:?}"
: "${IMAGE_TAG:?}"
FALLBACK_TAG="${TRELLIS_FALLBACK_TAG:-main}"

exists() {
  docker buildx imagetools inspect "$1" >/dev/null 2>&1
}

# compose variable suffix : image name in the registry
for entry in BACKEND:backend; do
  var="${entry%%:*}"
  name="${entry#*:}"
  if exists "$CI_REGISTRY_IMAGE/$name:$IMAGE_TAG"; then
    tag="$IMAGE_TAG"
  elif exists "$CI_REGISTRY_IMAGE/$name:$FALLBACK_TAG"; then
    tag="$FALLBACK_TAG"
    echo "note: $name not rebuilt in this pipeline, using :$FALLBACK_TAG" >&2
  else
    echo "error: neither $name:$IMAGE_TAG nor $name:$FALLBACK_TAG exists in $CI_REGISTRY_IMAGE" >&2
    exit 1
  fi
  echo "TRELLIS_${var}_VERSION=$tag"
done
