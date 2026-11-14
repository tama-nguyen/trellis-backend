# syntax=docker/dockerfile:1.28.0
#
# Python worker image (uv workspace in workers/, decision T14). One image per worker package; the
# `trellis` CLI is installed next to it and runs it: `trellis worker run <name>`.
#
#   docker buildx build -f docker/worker.Dockerfile \
#     --build-arg WORKER_PACKAGE=trellis-notice-stats --build-arg WORKER_NAME=notice-stats \
#     -t trellis/worker-notice-stats:local .
#
# Build context = repo root, filtered by docker/worker.Dockerfile.dockerignore.
#
# Targets: prod (default, last stage); debug (prod + debugpy, used by compose profile `debug`).
#
# Private package index: pass a netrc as a BuildKit secret (`--secret id=netrc,src=$HOME/.netrc`).
# Never put a token in a build ARG: ARG values end up in the image history.

ARG PYTHON_IMAGE=python:3.12.16-slim-trixie@sha256:5e2bdb1b0d542d5039cffb8181c65930093949194a65c365dfb1b0fe13a4fa5b
ARG UV_IMAGE=ghcr.io/astral-sh/uv:0.13.8@sha256:5cc1805e2426484abca4ad46ea783bab281fdb225fd82d8ce6d66a81254bac85

FROM ${UV_IMAGE} AS uv

# ---------------------------------------------------------------------------------------------------
# Only the files that decide dependency resolution. COPY --from is cached by content, so the
# dependency layer below is rebuilt when a pyproject.toml or uv.lock changes, not on every source edit.
FROM ${PYTHON_IMAGE} AS manifests
COPY workers/ /src/
RUN cd /src \
 && find . \( -name pyproject.toml -o -name uv.lock \) -print | while read -r f; do \
      mkdir -p "/manifests/$(dirname "$f")" && cp "$f" "/manifests/$f"; \
    done

# ---------------------------------------------------------------------------------------------------
FROM ${PYTHON_IMAGE} AS build
COPY --from=uv /uv /uvx /bin/
ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=never \
    UV_PROJECT_ENVIRONMENT=/app/.venv
ARG WORKER_PACKAGE=trellis-notice-stats
ARG CLI_PACKAGE=trellis-cli
# Space-separated extras of the worker package, e.g. "postgres". Kept explicit so the image carries only
# the transports it runs with.
ARG WORKER_EXTRAS=""
WORKDIR /src/workers
COPY --from=manifests /manifests/ ./
# --frozen: a lock that disagrees with pyproject.toml fails the build instead of re-resolving.
RUN --mount=type=cache,target=/root/.cache/uv \
    --mount=type=secret,id=netrc,target=/root/.netrc,required=false \
    extras=""; for e in ${WORKER_EXTRAS}; do extras="$extras --extra $e"; done; \
    uv sync --frozen --no-dev --no-install-workspace \
      --package "${WORKER_PACKAGE}" --package "${CLI_PACKAGE}" $extras
COPY workers/ ./
# --no-editable: the final image gets wheels in site-packages, not links back into /src.
RUN --mount=type=cache,target=/root/.cache/uv \
    --mount=type=secret,id=netrc,target=/root/.netrc,required=false \
    extras=""; for e in ${WORKER_EXTRAS}; do extras="$extras --extra $e"; done; \
    uv sync --frozen --no-dev --no-editable \
      --package "${WORKER_PACKAGE}" --package "${CLI_PACKAGE}" $extras

# ---------------------------------------------------------------------------------------------------
FROM ${PYTHON_IMAGE} AS prod
ARG WORKER_NAME=notice-stats
ARG VERSION=local
ARG REVISION=unknown
LABEL org.opencontainers.image.title="trellis-worker-${WORKER_NAME}" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.licenses="Apache-2.0"
# Patch OS packages fixed after the pinned base digest was published (DataHub does `apk upgrade`).
# Bump OS_PKG_REFRESH to force this layer to rebuild when a CVE fix lands; Trivy (ci/security.yml)
# tells you when.
ARG OS_PKG_REFRESH=2026-11-14
RUN apt-get update \
 && apt-get upgrade -y --no-install-recommends \
 && rm -rf /var/lib/apt/lists/*
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=app:app /app/.venv /app/.venv
# Workers validate events against the JSON Schemas in contracts/ (trellis_core env var CONTRACTS_DIR).
COPY --chown=app:app contracts/ /app/contracts/
ENV PATH="/app/.venv/bin:${PATH}" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    TRELLIS_CONTRACTS_DIR=/app/contracts \
    TRELLIS_WORKER_NAME=${WORKER_NAME} \
    TRELLIS_SERVICE_NAME=trellis-worker-${WORKER_NAME}
USER app
# TODO(wire): `trellis worker health <name>` must exit 0 only while the consumer loop is polling and
# its heartbeat is fresh (plan 10 A2.3: a real check, not DataHub's zombie-process check).
HEALTHCHECK --start-period=30s --interval=15s --timeout=5s --retries=4 \
  CMD trellis worker health "${TRELLIS_WORKER_NAME}" || exit 1
# Exec form cannot expand ENV, so a one-line sh does it. `exec` replaces the shell with Python so
# SIGTERM reaches the worker directly; compose adds `init: true` for zombie reaping.
ENTRYPOINT ["/bin/sh", "-c", "exec trellis worker run \"$TRELLIS_WORKER_NAME\" \"$@\"", "--"]

# ---------------------------------------------------------------------------------------------------
FROM prod AS debug
USER root
ARG DEBUGPY_VERSION=1.8.22
RUN --mount=from=uv,source=/uv,target=/bin/uv \
    uv pip install --python /app/.venv/bin/python --no-cache "debugpy==${DEBUGPY_VERSION}"
USER app
EXPOSE 5678
ENTRYPOINT ["/bin/sh", "-c", "exec python -m debugpy --listen 0.0.0.0:5678 /app/.venv/bin/trellis worker run \"$TRELLIS_WORKER_NAME\" \"$@\"", "--"]

# Default target last so a plain `docker build` produces the production image.
FROM prod
