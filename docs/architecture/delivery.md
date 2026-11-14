# Delivery: images, local stack, GitLab CI/CD, release and deploy

How Trellis code becomes running containers. Design and the DataHub comparison it comes from:
`../plan/10-docker-gitlab-cicd.md` (outside this repo). This page is the reference for what is
actually implemented.

| Piece | Where |
| --- | --- |
| Dockerfiles | `docker/backend.Dockerfile`, `docker/worker.Dockerfile`, `docker/web.Dockerfile` |
| Build-context whitelists | `docker/<name>.Dockerfile.dockerignore` (BuildKit uses these first), `.dockerignore` (fallback) |
| Local stack | `docker/compose.yml`, `docker/env/*.env`, `docker/.env.example`, `docker/observability/`, `docker/keycloak/` |
| Dev helper | `scripts/dev/trellis-dev.sh` (+ `trellis_dev.py`, stdlib Python 3.9+) |
| Pipeline | `.gitlab-ci.yml` + `ci/*.yml`, helper scripts in `scripts/ci/` |
| Release notes config | `ci/cliff.toml` (git-cliff) |
| MR template | `.gitlab/merge_request_templates/Default.md` |

## 1. Images

| Image (`$CI_REGISTRY_IMAGE/…`) | Dockerfile | Base (pinned tag + digest) | Contents | Entrypoint |
| --- | --- | --- | --- | --- |
| `backend` | `docker/backend.Dockerfile` | `eclipse-temurin:21.0.13_9-jre-noble` | Boot jar built by Gradle, extracted with `java -Djarmode=tools extract --layers --launcher` (layers: dependencies, loader, snapshot-deps, application) | `java org.springframework.boot.loader.launch.JarLauncher` |
| `worker-notice-stats` | `docker/worker.Dockerfile` | `python:3.12.16-slim-trixie`, `uv 0.13.8` | `.venv` from `uv sync --frozen --no-dev --no-editable --package trellis-notice-stats --package trellis-cli`, plus `contracts/` | `trellis worker run $TRELLIS_WORKER_NAME` |
| `web` | `docker/web.Dockerfile` | `node:22.23.5-trixie-slim` | Next.js standalone output; pnpm via corepack, `--frozen-lockfile` | `node server.js` |

Common rules:

- **Never compile in the backend image.** Gradle builds the jar (`./gradlew :trellis-app:bootJar`); the
  Dockerfile only packages it, so the Gradle build cache serves laptops and CI alike.
- **Non-root UID/GID 10001** (`app`) in every final stage.
- **Pinned bases** by tag and digest. A dated `OS_PKG_REFRESH` build arg runs `apt-get upgrade` in the
  final stage (DataHub's `apk upgrade`); bump it when Trivy reports a fixed OS CVE before the base
  image digest is bumped.
- **HEALTHCHECK** in each image: backend `GET :8081/actuator/health/readiness`; worker
  `trellis worker health <name>`; web `GET $TRELLIS_HEALTH_PATH` (default `/api/health`).
- **JVM flags** come from `JAVA_TOOL_OPTIONS` (default `-XX:MaxRAMPercentage=75
  -XX:+ExitOnOutOfMemoryError -XX:+UseG1GC`); override it per environment and keep those flags in.
- **OpenTelemetry Java agent is opt-in at build time:** `--build-arg OTEL_AGENT_VERSION=2.16.0
  --build-arg OTEL_AGENT_SHA256=<sha256>` puts it at `/app/otel/opentelemetry-javaagent.jar` (checksum
  verified); enable it with `-javaagent:/app/otel/opentelemetry-javaagent.jar` in `JAVA_TOOL_OPTIONS`.
  The default is Spring Boot's own OTel starter.
- **Build targets:** backend `prod` (default) / `dev` (JRE and user only, the debug profile bind-mounts
  the jar); worker `prod` (default) / `debug` (+ debugpy).
- **Secrets at build time** only through BuildKit secrets (`--secret id=netrc,...` for a private Python
  index), never `ARG`.
- **`NEXT_PUBLIC_*` is baked at build time.** Per-environment values (backend origin, IdP URL) must be
  read at runtime by the Next server, or "build once, promote by retag" breaks.

Local build:

```bash
./gradlew :trellis-app:bootJar
scripts/dev/trellis-dev.sh up --profile app --build      # builds backend, worker, web via compose
# or one image:
docker buildx build -f docker/web.Dockerfile -t trellis/web:local .
```

## 2. Tags

| Context | Tag | Mutable | Created by |
| --- | --- | --- | --- |
| MR pipeline | `mr<iid>-<short_sha>` | no | `docker:build:*` |
| main pipeline | `sha-<short_sha>` | no | `docker:build:*` |
| main, smoke passed | `main` | yes (floating) | `docker:tag-main` (retag, no build) |
| tag `vX.Y.Z` | `vX.Y.Z` and `X.Y` | `vX.Y.Z` no, `X.Y` yes | `docker:promote` (retag of `sha-<short_sha>`) |
| tag `vX.Y.Z-rc.N` | `vX.Y.Z-rc.N` | no | `docker:promote` |
| registry build cache | `buildcache` | yes | written by main only |
| local | `local`, `debug` | yes | compose / `docker build` |

`short_sha` is `$CI_COMMIT_SHORT_SHA` (8 characters). Labels: `org.opencontainers.image.{source,revision,
created,version,title,licenses}`. Each push carries an SBOM and max-mode provenance attestation
(`--sbom=true --provenance=mode=max`); retagging with `imagetools create` keeps them.

Registry cleanup policy to set in GitLab (Settings > Packages and registries): remove tags matching
`^mr\d+-.*` older than 14 days and `^sha-.*` older than 90 days; keep `^v.*`, `main`, `buildcache`.

## 3. Local stack: compose profiles and ports

| Profile | Services | Use |
| --- | --- | --- |
| `minimal` | postgres | apps on the host (`./gradlew :trellis-app:bootRun`, `pnpm dev`, `uv run`) |
| `app` | + setup, backend, worker, web | test the images; events use the in-memory transport |
| `full` | + kafka, redis, minio, keycloak, otel-collector, tempo, loki, prometheus, grafana (+ probe-tools) | demo, CI smoke |
| `debug` | full infra + setup + web + `backend-debug` (bind-mounted jar, JDWP) + `worker-debug` (debugpy) | attach a debugger to containers |

Start order: infrastructure healthchecks → `setup` (one-shot: Liquibase update, Kafka topics, S3 bucket,
then exit 0) → apps (`depends_on: setup: service_completed_successfully`). Dependencies on services of
another profile use `required: false` (verified: with profile `app`, a `required: false` dependency
on a `full`-only service is skipped). Needs Docker Compose 2.24+.

Every infra service has a real healthcheck. The distroless images (otel-collector, tempo, loki) have no
shell, so the one-shot `probe-tools` service copies a static busybox into a volume they mount read-only
and their healthchecks run `/probe/busybox wget …`.

### Port map (slot 0; slot N adds N×1000)

All host ports bind to `127.0.0.1` (`TRELLIS_BIND`; CI sets `0.0.0.0`). The Trellis range was chosen to
avoid the standard ports other projects on the same machine use.

| Service | Container port | Host port | Variable |
| --- | --- | --- | --- |
| postgres | 5432 | 15432 | `TRELLIS_PG_PORT` |
| web | 3000 | 13000 | `TRELLIS_WEB_PORT` |
| backend API | 8080 | 18080 | `TRELLIS_BACKEND_PORT` |
| backend actuator | 8081 | 18081 | `TRELLIS_BACKEND_MGMT_PORT` |
| kafka (EXTERNAL) | 9092 | 19092 | `TRELLIS_KAFKA_PORT` (containers use `kafka:29092`) |
| redis | 6379 | 16379 | `TRELLIS_REDIS_PORT` |
| minio S3 / console | 9000 / 9001 | 19000 / 19001 | `TRELLIS_MINIO_PORT`, `TRELLIS_MINIO_CONSOLE_PORT` |
| keycloak | 8080 | 18180 | `TRELLIS_KEYCLOAK_PORT` |
| otel-collector OTLP gRPC / HTTP | 4317 / 4318 | 14317 / 14318 | `TRELLIS_OTLP_GRPC_PORT`, `TRELLIS_OTLP_HTTP_PORT` |
| prometheus | 9090 | 19090 | `TRELLIS_PROMETHEUS_PORT` |
| grafana | 3000 | 13300 | `TRELLIS_GRAFANA_PORT` |
| tempo | 3200 | 13200 | `TRELLIS_TEMPO_PORT` |
| loki | 3100 | 13100 | `TRELLIS_LOKI_PORT` |
| backend-debug JDWP | 5005 | 15005 | `TRELLIS_BACKEND_DEBUG_PORT` |
| worker-debug debugpy | 5678 | 15678 | `TRELLIS_WORKER_DEBUG_PORT` |

Slots 0–4 use host ports 13000–23092 with no overlap between slots (checked by computing all 85).

### Environment layering

Two separate things:

1. **Compose interpolation** (`${VAR:-default}` in `compose.yml`: images, tags, ports, dev passwords):
   defaults in `compose.yml` → `docker/.env` (copy of `docker/.env.example`, gitignored) → shell
   environment (what `trellis-dev.sh` and CI export; wins).
2. **Container environment:** `docker/env/common.env` → `docker/env/<service>.env` (`backend`, `setup`,
   `worker`, `web`; committed, non-secret) → `docker/env/.env.local` (gitignored, optional) → the
   `environment:` block in `compose.yml` (values that depend on interpolation, e.g. passwords).

Images and versions are variables: `TRELLIS_REGISTRY` (default `trellis`), `TRELLIS_VERSION` (default
`local`), and per service `TRELLIS_{BACKEND,WORKER,WEB}_IMAGE` / `_VERSION`, so a released backend can
run with a local web. Infra images: `TRELLIS_{POSTGRES,KAFKA,REDIS,MINIO,KEYCLOAK,OTEL_COLLECTOR,TEMPO,
LOKI,PROMETHEUS,GRAFANA,BUSYBOX}_IMAGE`.

MinIO: upstream stopped publishing images in 2025; the stack uses `pgsty/minio` (community build of
the same server, same env vars and API). Replace the image variable if your organisation mirrors another.

### Dev helper

```bash
scripts/dev/trellis-dev.sh up --profile full     # up -d --wait, then prints the port map of what runs
scripts/dev/trellis-dev.sh status                # containers + health of this slot
scripts/dev/trellis-dev.sh logs backend -f
scripts/dev/trellis-dev.sh rebuild backend-debug # after ./gradlew :trellis-app:bootJar
scripts/dev/trellis-dev.sh ports                 # port map of this slot
scripts/dev/trellis-dev.sh down                  # keep volumes
scripts/dev/trellis-dev.sh reset                 # down -v for this slot's project only, asks first
```

**Worktree slots.** The main checkout is slot 0 (project `trellis`). A linked git worktree gets the
lowest free slot 1–4 on first use (project `trellis-s<N>`, ports +N×1000), remembered in
`<worktree git dir>/trellis-dev-slot`, so it is never committed. `--slot N` or `TRELLIS_SLOT=N`
overrides. `up --profile full|debug` also sets `TRELLIS_EVENTS_TRANSPORT=kafka` and asks `setup` to
create topics and the bucket. Ports exported by the helper win over `docker/.env`.

## 4. Pipeline

```text
.pre      ci:vars ─────────────────────────────────────────────────────────────┐ IMAGE_TAG (dotenv)
lint      mr:title · java:lint · py:lint · web:lint · docker:compose-config · ci:refs
build     java:build (boot jar artifact) · web:build (+budget) · cache:warm:{gradle,uv,pnpm} (main only)
test      java:test · py:test · web:test · secret_detection (GitLab template)
package   docker:build:backend (needs java:build) · docker:build:worker · docker:build:web
scan      trivy:fs · trivy:image:{backend,worker,web} (CycloneDX SBOM artifact)
smoke     smoke:compose  (compose --profile full up --wait, scripts/ci/smoke.sh, logs artifact)
release   docker:tag-main (main) · docker:promote, release:notes, release (tags)
deploy    deploy:dev (main, automatic) · deploy:prod (final tags, manual)
```

`needs:` makes it a DAG: tests start without waiting for lint, image builds start as soon as their
input exists. What runs where (output of `scripts/ci/check_ci_refs.py`, which simulates the rules):

| Pipeline | Jobs |
| --- | --- |
| MR touching `backend/` | ci:vars, mr:title, java:lint, java:build, java:test, docker:build:backend, trivy:fs, trivy:image:backend, smoke:compose (+ secret_detection) |
| MR touching `workers/` | ci:vars, mr:title, py:lint, py:test, docker:build:worker, trivy:fs, trivy:image:worker, smoke:compose |
| MR touching `web/` | ci:vars, mr:title, web:lint, web:build, web:test, docker:build:web, trivy:fs, trivy:image:web, smoke:compose |
| MR touching `contracts/` | backend + workers + web sets |
| MR touching docs only | ci:vars, mr:title, trivy:fs |
| push to main | everything (24 jobs) incl. cache warm-up, docker:tag-main, deploy:dev |
| tag `vX.Y.Z` | ci:vars, docker:promote, release:notes, release, deploy:prod (manual) |
| tag `vX.Y.Z-rc.N` | same without deploy:prod |

Workflow rules: MR pipelines for MRs; no duplicate branch pipeline while an MR is open; main, release
tags, schedules, manual/API runs, and branches without an MR. `auto_cancel: on_new_commit: interruptible`
cancels superseded MR/branch pipelines; main overrides it to `none` so every main commit has a complete
result. Deploy jobs are `interruptible: false`.

Images not rebuilt in an MR (path rules) are served from the floating `main` tag in the smoke test
(`scripts/ci/resolve-images.sh`); smoke prints `docker compose config --images` so the tested images are
visible in the log.

### Reports in the MR

| Job | Report |
| --- | --- |
| java:test | JUnit (`backend/**/build/test-results/test/TEST-*.xml`), JaCoCo coverage (`coverage_format: jacoco`, GitLab 17.6+) |
| py:lint | Code Quality (ruff `--output-format=gitlab`) |
| py:test, web:test | JUnit |
| smoke:compose | JUnit (`smoke-junit.xml`), compose logs as artifact |
| trivy:image:* | CycloneDX SBOM (`artifacts:reports:cyclonedx`; shown on Ultimate, kept as artifact everywhere) |

### Cache policy

| Cache | Key | Paths | Readers | Writer |
| --- | --- | --- | --- | --- |
| Gradle | `gradle` + hash of `gradle/wrapper/gradle-wrapper.properties`, `gradle/libs.versions.toml` | `.gradle-home/caches/{modules-2,build-cache-1}`, `.gradle-home/wrapper` | java:* (`policy: pull`) | `cache:warm:gradle` on main |
| uv | `uv` + hash of `workers/uv.lock` | `.cache/uv` | py:* | `cache:warm:uv` on main |
| pnpm | `pnpm` + hash of `web/pnpm-lock.yaml` | `.pnpm-store` | web:* | `cache:warm:pnpm` on main |
| Trivy DB | `trivy-db` | `.trivycache` | trivy:* | trivy:* (pull-push; DB is not code) |
| Docker layers | registry `…/<image>:buildcache` | — | every image build | main only (`--cache-to`) |

GitLab separates caches of protected and unprotected refs by default (`-protected` / `-non_protected`
key suffix), so MR pipelines cannot read what main wrote. To share, clear **Settings > CI/CD > General
pipelines > Use separate caches for protected branches**. Only do it if every Developer is trusted: an
MR could then poison a shared key; the read-only policy on MR jobs narrows, but does not remove, that
risk. Caches are shared across runners only with a distributed cache (S3/MinIO) configured.

## 5. Variables and secrets

Project-level CI/CD variables (Settings > CI/CD > Variables). Masked = hidden in logs; Protected =
only on protected branches/tags; environment scope limits it to one deploy target.

| Variable | Type | Protected | Masked | Scope | Used by |
| --- | --- | --- | --- | --- | --- |
| `CI_REGISTRY_*`, `CI_JOB_TOKEN` | predefined | — | yes | all | image push/pull in CI |
| `DEPLOY_SSH_HOST`, `DEPLOY_SSH_USER` | variable | yes | no | `dev`, `production` | deploy jobs |
| `DEPLOY_SSH_PRIVATE_KEY` | **File** | yes | n/a | `dev`, `production` | deploy jobs |
| `DEPLOY_SSH_KNOWN_HOSTS` | **File** | yes | n/a | `dev`, `production` | deploy jobs (pinned host key) |
| `DEPLOY_ENV_FILE` | **File** | yes | n/a | per environment | becomes `docker/env/.env.local` on deploy |
| `DEPLOY_REGISTRY_USER`, `DEPLOY_REGISTRY_PASSWORD` | variable | yes | password: masked and hidden | per environment | deploy token (`read_registry`) the host keeps using after the job |
| `TRELLIS_DB_PASSWORD`, `TRELLIS_MINIO_ROOT_PASSWORD` | variable | yes | masked and hidden | per environment | compose interpolation on deploy |
| `AWS_ROLE_ARN` (or cloud equivalent) | variable | yes | no | `production` | OIDC exchange of `CLOUD_ID_TOKEN` (placeholder) |

Release tags `v*` must be **protected tags** so protected variables reach `deploy:prod`. Long-lived cloud
keys are not used: `deploy:prod` declares `id_tokens: CLOUD_ID_TOKEN` for an OIDC exchange whose trust
policy is restricted to this project, `ref_protected=true` and `environment=production`.

## 6. Release flow

1. Merge to main (squash; commit message = MR title, Conventional Commits, checked by `mr:title`).
2. main pipeline: build `sha-<sha>` images → scan → smoke → `docker:tag-main` adds `main` → `deploy:dev`.
3. Cut a release by tagging **a main commit whose pipeline passed**: `git tag v1.4.0 <sha> && git push origin v1.4.0`
   (or `-rc.1` for a candidate).
4. Tag pipeline: `docker:promote` adds `v1.4.0` and `1.4` to the existing `sha-<sha>` images (fails if
   they do not exist; nothing is rebuilt) → `release:notes` (git-cliff, `ci/cliff.toml`, breaking
   changes link to `docs/how/breaking-changes.md`) → `release` creates the GitLab release (glab CLI;
   release-cli is deprecated).
5. `deploy:prod` waits for a manual click.

Trunk-based for v0.1: no release branches. Hotfix = fix on main, tag a new patch version.

## 7. Deploy flow

Compose on one VM per environment, driven from CI over SSH (`DOCKER_HOST=ssh://…`): nothing is copied
to the host, it only needs Docker and an SSH user allowed to use it. Profile `app`; project name
`trellis-<environment>`; `docker compose pull` then `up -d --no-build --wait`; `setup` runs Liquibase
before the apps start. `resource_group` per environment serialises deploys. TLS and the public hostname
are a reverse proxy on the host (ports bind to 127.0.0.1).

- **dev**: automatic on main after `docker:tag-main`, tag `sha-<sha>`.
- **production**: manual on a final release tag, tag `vX.Y.Z`. Approval rules and protected
  environments need GitLab Premium; on Free, protect the tag and limit who can run the manual job.

## 8. Runbook

| Situation | Action |
| --- | --- |
| Roll back an app | Re-run the deploy job of the previous good pipeline (Deployments > Environments > *Re-deploy*), or tag-deploy the previous version. Same images, nothing rebuilt. |
| Roll back after a schema change | Liquibase migrations are forward-only. Ship a new migration that reverts the change, then deploy; only then roll the app back if still needed. |
| `smoke:compose` fails | Open the `docker-logs/` artifact (`compose.log`, `ps.txt`) and `images.env` to see exactly which tags ran. Reproduce: `TRELLIS_REGISTRY=$CI_REGISTRY_IMAGE TRELLIS_VERSION=<tag> scripts/dev/trellis-dev.sh up --profile full`. |
| `docker:promote` says source image missing | The tag points at a commit without a green main pipeline. Delete the tag, tag a commit that passed. |
| Trivy fails on an OS package with a fix | Bump `OS_PKG_REFRESH` (or the base digest) in the Dockerfile. App dependency: upgrade it. Accepting a finding: add it to `ci/trivyignore` with reason and expiry. |
| MR pipelines never hit the cache | See "Cache policy" (protected cache separation). |
| Local ports clash with another project | `scripts/dev/trellis-dev.sh --slot N up` or set the `TRELLIS_*_PORT` variable. |
| Local data is broken | `scripts/dev/trellis-dev.sh reset` (only this slot's project volumes). |

## 9. What v0.1.0 does, and what comes later

v0.1.0 (implemented here): the three images; compose profiles minimal/app/full/debug with healthchecks
and a setup job; per-worktree dev slots; the pipeline above (path rules on MRs, read-only caches + main
warm-up, JUnit/coverage/code quality, MR title check, DinD + buildx with registry cache, Trivy fs + image
gate, GitLab Secret Detection, compose smoke, retag-only promotion, git-cliff release notes, compose-over-SSH
deploys with `resource_group`, `environment` and an OIDC placeholder).

Later: CI toolchain image built from `mise.toml` (one image with JDK, uv, Node, git for every job);
`docker-bake.hcl`; arm64 images; cosign keyless signing; Dependency/Container Scanning templates
(Ultimate); weighted test sharding via child pipelines; smoke in several batches and Playwright;
review apps; staging with approvals (Premium); self-hosted runner with distributed cache and Gradle
remote build cache; Helm chart + GitOps; release branches and hotfix flow; Renovate for image digests.

## 10. Requests to other areas

These are needed for the pieces marked `TODO(wire)` in `docker/`, `ci/` and `scripts/`. Until done, the
corresponding image or job fails loudly rather than silently.

| Area | Request | Why / where it bites |
| --- | --- | --- |
| backend | A main class in `trellis-app` so `./gradlew :trellis-app:bootJar` produces the boot jar (today: "Main class name has not been configured"). Optionally `bootJar { archiveFileName = "trellis-app.jar" }`. | `java:build`, `docker:build:backend`; the Dockerfile accepts any single jar in `backend/apps/app/build/libs/`. |
| backend | A `setup` Spring profile: with `SPRING_MAIN_WEB_APPLICATION_TYPE=none`, run Liquibase, create Kafka topics when `TRELLIS_SETUP_KAFKA_TOPICS=true`, create the S3 bucket when `TRELLIS_SETUP_S3_BUCKET=true`, then exit 0. | compose `setup` service (`docker/env/setup.env`); every app waits for it. |
| backend | Actuator on `management.server.port=8081` with health probes (`/actuator/health/readiness`) and `/actuator/prometheus` exposed there; app on 8080. | image HEALTHCHECK, Prometheus scrape, smoke. |
| backend | JaCoCo: `jacocoTestReport` with XML output, wired into `check`. | `java:test` coverage report. |
| backend | Read S3 settings from `TRELLIS_S3_{ENDPOINT,BUCKET,ACCESS_KEY,SECRET_KEY}` and the events transport from `TRELLIS_EVENTS_TRANSPORT`, or tell me the names to use. | `docker/compose.yml` `x-backend-env`. |
| backend | `docker/compose.backend.yml` (yours): please reuse the `TRELLIS_*` port variables and project name `trellis` from `docker/compose.yml`, or `include:` it, so the two never start two Postgres on the same port. | port clashes |
| workers | `trellis worker health <name>`: exit 0 only while the consumer loop polls and its heartbeat is fresh. | worker image HEALTHCHECK, `compose up --wait`. |
| workers | Settings for the transports: `TRELLIS_EVENTS_TRANSPORT`, `TRELLIS_KAFKA_BOOTSTRAP_SERVERS`, `TRELLIS_DATABASE_URL` (or tell me the names). A long-running transport for containers: with `inmemory` the worker drains and exits 0, which fails `up --wait` in profile `app`. | `x-worker-env` in compose. |
| workers | `pytest-cov` in the dev dependency group. | `py:test` coverage report. |
| web | `output: "standalone"` in `next.config.ts` when `TRELLIS_NEXT_OUTPUT=standalone` (the Dockerfile sets it), e.g. `output: process.env.TRELLIS_NEXT_OUTPUT === "standalone" ? "standalone" : undefined`. Verified on a scratch copy: the image then builds, runs as UID 10001 and becomes healthy. | `docker:build:web` fails until then. |
| web | `src/app/api/health/route.ts`: 200 without auth and without calling the backend. | web image HEALTHCHECK (`TRELLIS_HEALTH_PATH`). |
| web | Read the backend origin at runtime on the server (`TRELLIS_BACKEND_URL`, set by compose to `http://backend:8080`), e.g. a route handler or middleware proxy for `/api/*`; not `NEXT_PUBLIC_*`. | build once, promote by retag. |
| web | Fix two HIGH CVEs Trivy finds in `web/pnpm-lock.yaml`: `postcss` 8.4.31 (transitive) needs ≥ 8.5.18 (CVE-2026-45623, CVE-2026-73646), e.g. a `pnpm.overrides` entry. | `trivy:fs` gate fails today. |
| web | `@vitest/coverage-v8` in devDependencies (optional). | `web:test` coverage report. |
| root | `.gitignore`: `docker/.env` and `docker/env/.env.local` are already covered by `.env` / `.env.*`; nothing to add. Add `mise.toml` entries for Java 21 and Python 3.12 / uv when convenient (the CI toolchain image will be built from it). | — |
