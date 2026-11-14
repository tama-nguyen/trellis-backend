# syntax=docker/dockerfile:1.28.0
#
# Web image: Next.js `output: "standalone"` (web/next.config.ts) run by plain Node, no pnpm at runtime.
#
#   docker buildx build -f docker/web.Dockerfile -t trellis/web:local .
#
# Build context = repo root, filtered by docker/web.Dockerfile.dockerignore.
#
# NEXT_PUBLIC_* values are inlined at build time. Anything that differs per environment (backend
# origin, IdP URL) must be read at runtime on the server instead, or "build once, promote by retag"
# (docs/architecture/delivery.md) breaks. The build args below are for values that are the same
# everywhere.

ARG NODE_IMAGE=node:22.23.5-trixie-slim@sha256:68c76cfad89921b927eae28ba21407475e29d0ce450afcdbe70c1532db042b64

FROM ${NODE_IMAGE} AS deps
# pnpm comes from the `packageManager` field in web/package.json through corepack, so the version is
# pinned in one place. Corepack verifies the download signature.
ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0 \
    PNPM_HOME=/pnpm \
    NEXT_TELEMETRY_DISABLED=1
RUN corepack enable
WORKDIR /repo/web
COPY web/package.json web/pnpm-lock.yaml web/pnpm-workspace.yaml ./
# --frozen-lockfile: a lock that disagrees with package.json fails the build.
RUN --mount=type=cache,id=trellis-pnpm-store,target=/pnpm/store \
    pnpm config set store-dir /pnpm/store \
 && pnpm install --frozen-lockfile

# ---------------------------------------------------------------------------------------------------
FROM deps AS build
ARG NEXT_PUBLIC_MOCK_DOMAINS=false
ARG NEXT_PUBLIC_ANALYTICS_PROVIDER=none
ENV NEXT_PUBLIC_MOCK_DOMAINS=${NEXT_PUBLIC_MOCK_DOMAINS} \
    NEXT_PUBLIC_ANALYTICS_PROVIDER=${NEXT_PUBLIC_ANALYTICS_PROVIDER} \
    NODE_ENV=production \
    TRELLIS_NEXT_OUTPUT=standalone
COPY web/ ./
# web/next.config.ts sets `output: "standalone"` only when TRELLIS_NEXT_OUTPUT=standalone (so
# `pnpm start` keeps working on laptops). The check below turns a silent non-standalone build into a
# clear error instead of a confusing "COPY .next/standalone: not found".
# public/ is optional in Next; create it so the COPY in the final stage never fails.
RUN mkdir -p public && pnpm build
RUN test -f .next/standalone/server.js \
 || { echo "web/.next/standalone/server.js missing: next.config.ts must honour TRELLIS_NEXT_OUTPUT=standalone" >&2; exit 1; }

# ---------------------------------------------------------------------------------------------------
FROM ${NODE_IMAGE} AS prod
ARG VERSION=local
ARG REVISION=unknown
LABEL org.opencontainers.image.title="trellis-web" \
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
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0 \
    TRELLIS_HEALTH_PATH=/api/health
COPY --from=build --chown=app:app /repo/web/.next/standalone/ ./
COPY --from=build --chown=app:app /repo/web/.next/static/ ./.next/static/
COPY --from=build --chown=app:app /repo/web/public/ ./public/
USER app
EXPOSE 3000
# web/src/app/api/health/route.ts answers 200 without auth and without calling the backend
# (liveness of the Node process only).
HEALTHCHECK --start-period=20s --interval=15s --timeout=3s --retries=4 \
  CMD node -e "fetch('http://127.0.0.1:'+process.env.PORT+process.env.TRELLIS_HEALTH_PATH,{redirect:'manual'}).then(r=>process.exit(r.status<400?0:1)).catch(()=>process.exit(1))"
CMD ["node", "server.js"]
