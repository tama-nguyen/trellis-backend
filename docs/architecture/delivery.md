# Delivery: local images and GitHub build verification

GitHub Actions builds and verifies this repository. Release creation, registry publication and
deployment are deferred under [ADR 0007](../adr/0007-github-build-verification.md).

## GitHub Actions

[CI workflow](../../.github/workflows/ci.yml) runs on pull requests, pushes to `main`, merge queues
and manual dispatch. There are no path filters, so required checks also complete for documentation
changes. Feature branches get automatic feedback through their PR; a branch can also be selected
when dispatching manually after the workflow is available on the default branch.

| Job | Verification and output |
| --- | --- |
| Backend build | JDK 21, Gradle wrapper validation, Spotless, compile, JUnit/ArchUnit, Testcontainers, API contract drift, JaCoCo, boot JAR, local container build |
| Contract packages | Pinned Python/TypeScript generators in check mode, frozen pnpm install, TypeScript compile, Python wheel/sdist build, committed-output drift |

Ubuntu 24.04 hosted runners provide a local Docker daemon. Tests create and clean up their own
containers; neither Docker-in-Docker nor a running shared Compose stack is needed.

Manual **Run workflow** offers boolean `run_tests`, default `true`. With `false`, Gradle uses
`build -x test -x checkApiContracts`: formatting, compilation, packaging and generated-client checks
still run, while JUnit, ArchUnit and the Docker-backed API export check are skipped. PR, push and
merge-queue events always run tests. A manual build without tests is not equivalent to a passing PR
check.

Reports are uploaded even after failure (unless cancelled), with seven-day retention. The JAR and
contract distributions are downloadable artifacts. Container images are built locally and discarded
with the runner; no login, push, package publish, release, SSH or deploy step exists.

## Security and maintenance

Tools are installed from `mise.toml`; workflow YAML does not duplicate their versions.
Actions are pinned to full commit SHAs, with weekly Dependabot updates. Workflow tokens have only
`contents: read`; checkout credentials are not persisted. PR code runs through `pull_request`, not
`pull_request_target`. Gradle uses the basic GitHub cache provider, with writes restricted to the
default branch. Configuration-cache entries are not shared without an encryption key. No Build Scan
publication is enabled. Superseded PR runs are cancelled and every default-branch run can finish.

## Repository setup

1. In **Settings → Actions → General**, enable Actions and permit the pinned `actions/*`,
   `gradle/actions` and `jdx/mise-action` actions. Choose read-only workflow
   permissions. No repository secret is required.
2. Merge the workflow into the default branch to expose **Actions → CI → Run workflow**.
3. Run once with `run_tests=true`, then make **Backend build** and **Contract packages** required
   checks in the branch ruleset. Require pull requests and prevent direct pushes as appropriate.
4. Review Dependabot action updates. Registry, release and deployment settings belong to a later
   approved workflow.

## Local image

`build-logic/docker/backend.Dockerfile` packages the boot JAR rather than recompiling Java.
The image uses a non-root user, a pinned JRE base and a management-port readiness probe.

```sh
./gradlew build jacocoTestReport
docker build -f build-logic/docker/backend.Dockerfile -t trellis/backend:local .
build-logic/scripts/ci/smoke.sh
```

The smoke script requires the local Compose stack to be running and is not part of CI.

## Local stack: compose profiles and ports

| Profile | Services | Use |
| --- | --- | --- |
| `minimal` | postgres | apps on the host (`./gradlew :trellis-app:bootRun`, `pnpm dev`, `uv run`) |
| `app` | + setup, backend, worker, web | test the images; events use the in-memory transport |
| `full` | + kafka, redis, minio, keycloak, otel-collector, tempo, loki, prometheus, grafana (+ probe-tools) | local demo and smoke |
| `debug` | full infra + setup + web + `backend-debug` (bind-mounted jar, JDWP) + `worker-debug` (debugpy) | attach a debugger to containers |

Start order: infrastructure healthchecks → `setup` (one-shot: Liquibase update, Kafka topics, S3 bucket,
then exit 0) → apps (`depends_on: setup: service_completed_successfully`). Dependencies on services of
another profile use `required: false` (verified: with profile `app`, a `required: false` dependency
on a `full`-only service is skipped). Needs Docker Compose 2.24+.

Every infra service has a real healthcheck. The distroless images (otel-collector, tempo, loki) have no
shell, so the one-shot `probe-tools` service copies a static busybox into a volume they mount read-only
and their healthchecks run `/probe/busybox wget …`.

### Port map (slot 0; slot N adds N×1000)

All host ports bind to `127.0.0.1` (`TRELLIS_BIND`). The Trellis range was chosen to
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
   defaults in `compose.yml` → `build-logic/docker/.env` (copy of `build-logic/docker/.env.example`, gitignored) → shell
   environment (what `trellis-dev.sh` and CI export; wins).
2. **Container environment:** `build-logic/docker/env/common.env` → `build-logic/docker/env/<service>.env` (`backend`, `setup`,
   `worker`, `web`; committed, non-secret) → `build-logic/docker/env/.env.local` (gitignored, optional) → the
   `environment:` block in `compose.yml` (values that depend on interpolation, e.g. passwords).

Images and versions are variables: `TRELLIS_REGISTRY` (default `trellis`), `TRELLIS_VERSION` (default
`local`), and per service `TRELLIS_{BACKEND,WORKER,WEB}_IMAGE` / `_VERSION`, so a released backend can
run with a local web. Infra images: `TRELLIS_{POSTGRES,KAFKA,REDIS,MINIO,KEYCLOAK,OTEL_COLLECTOR,TEMPO,
LOKI,PROMETHEUS,GRAFANA,BUSYBOX}_IMAGE`.

MinIO: upstream stopped publishing images in 2025; the stack uses `pgsty/minio` (community build of
the same server, same env vars and API). Replace the image variable if your organisation mirrors another.

### Dev helper

```bash
build-logic/scripts/dev/trellis-dev.sh up --profile full     # up -d --wait, then prints the port map of what runs
build-logic/scripts/dev/trellis-dev.sh status                # containers + health of this slot
build-logic/scripts/dev/trellis-dev.sh logs backend -f
build-logic/scripts/dev/trellis-dev.sh rebuild backend-debug # after ./gradlew :trellis-app:bootJar
build-logic/scripts/dev/trellis-dev.sh ports                 # port map of this slot
build-logic/scripts/dev/trellis-dev.sh down                  # keep volumes
build-logic/scripts/dev/trellis-dev.sh reset                 # down -v for this slot's project only, asks first
```

**Worktree slots.** The main checkout is slot 0 (project `trellis`). A linked git worktree gets the
lowest free slot 1–4 on first use (project `trellis-s<N>`, ports +N×1000), remembered in
`<worktree git dir>/trellis-dev-slot`, so it is never committed. `--slot N` or `TRELLIS_SLOT=N`
overrides. `up --profile full|debug` also sets `TRELLIS_EVENTS_TRANSPORT=kafka` and asks `setup` to
create topics and the bucket. Ports exported by the helper win over `build-logic/docker/.env`.

## 4. Pipeline

## References

- [GitHub secure use](https://docs.github.com/en/actions/reference/security/secure-use)
- [Gradle on GitHub Actions](https://docs.gradle.org/current/userguide/github-actions.html)
