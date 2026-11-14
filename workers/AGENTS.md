# workers/ — Python workers, SDK and CLI

Rules for everything under `workers/`. The repo-wide rules in `../AGENTS.md` apply too and are not
repeated. Architecture, pattern catalogue and delivery semantics: `../docs/architecture/python.md`.

## Commands

Run from `workers/`. Always through `uv run`, so the locked tool versions apply.

```bash
uv sync                          # create/refresh .venv from uv.lock (Python 3.12 is fetched by uv)
uv run ruff format               # format (CI: uv run ruff format --check)
uv run ruff check                # lint; --fix for the safe fixes
uv run mypy                      # strict type check of packages, workers and tests
uv run pytest                    # tooling + contract tests (integration tests deselected)
uv run pytest --cov              # same + coverage.xml (Cobertura, for CI)
uv run pytest -m integration     # starts its own Postgres 17 container (needs Docker)
uv run python ../contracts/codegen/python/generate.py          # regenerate event models
uv run python ../contracts/codegen/python/generate.py --check  # CI: fail if they are stale
uv run python ../contracts/codegen/python/generate_sdk.py          # regenerate the API client
uv run python ../contracts/codegen/python/generate_sdk.py --check  # CI: fail if it is stale
uv run trellis --help            # the CLI
uv run trellis worker run notice-stats --input ../contracts/examples/notice.published.v1.json
uv run trellis worker health notice-stats   # probe a running worker's /health/ready
```

Add a dependency to the package that needs it (`uv add --package trellis-events httpx`), never to
the workspace root. Every version constraint that is not the obvious "next major" gets a comment
saying why (CVE id, upstream bug).

## Layout

```
pyproject.toml          workspace root (virtual): tool config, dev dependencies
packages/trellis-core   config, env, secrets, logging, tracing, backoff, ids, plugins, reports
packages/trellis-events envelope, generated models, pipeline engine, inbox, transports, health,
                        worker API
packages/trellis-sdk    HTTP client for the backend + OpenAPI-generated models and resources
packages/trellis-cli    the `trellis` command
workers/<name>          one deployable worker per folder, registered by entry point
tests/<package>         tests mirror the package they cover
```

Each package uses the `src/` layout and ships `py.typed`. `__init__.py` files contain a docstring
only: no re-exports (import the module that owns the thing).

## Typing

- `mypy --strict` plus `disallow_any_explicit`: no `Any`, anywhere. Untyped JSON is
  `trellis_core.json_types.JsonValue`/`JsonObject`; validate foreign data with `as_json_value`.
- Pydantic for data from **outside** (config, envelopes, payloads, HTTP bodies); `@dataclass` for
  internal data and reports; `Protocol` for interfaces; `TypedDict` only for raw JSON in tests.
- Python 3.12 syntax: `class Foo[T]`, `type Alias = ...`, `X | None`, builtin generics.
- Report and log *messages* are constants (`LiteralString` in `Report`); variable data goes in
  fields/`context`, never in an f-string message.

## Configuration, environment, secrets

- Configuration classes extend `trellis_core.config.ConfigModel` (`extra="forbid"`, no input in
  validation errors, secrets registered for masking). Log or print config only via
  `model_dump_redacted()`.
- Environment variables are read **only** in `trellis_core/env_vars.py` (ruff bans `os.environ`
  and `os.getenv` elsewhere). Add a typed field there with a docstring; prefix `TRELLIS_`.
- Every credential is `SecretStr`. Never put a secret into `os.environ`, a URL query string, a log
  message or an exception message you build yourself.
- Config files are `.toml` or `.json`; `${VAR}` is expanded, an unset variable is an error, and every
  expanded value is treated as a secret (masked in logs).

## Logging and tracing

- `log = get_logger(__name__)`; `log.info("event happened", key=value)`. No `print` (ruff `T20`),
  no `logging.getLogger` in library code, no %-formatting.
- Logs are JSON lines on **stderr**; stdout is reserved for command output.
- Per-unit context with `bound_log_context(...)`; the pipeline already binds `event_id`,
  `correlation_id`, `trace_id`, `topic`, `partition`, `offset`, `consumer`.
- Levels: ERROR = a human must act; WARNING = unusual but handled; INFO = lifecycle; DEBUG = off in
  production.
- Tracing is OpenTelemetry, off unless `TRELLIS_OTEL_ENABLED=true`. No product telemetry, ever.

## Decorators (the Python counterpart of the Java annotations)

| Decorator | Module | Use for |
| --- | --- | --- |
| `@plugin(name=, description=, config=, status=)` | `trellis_core.plugins` | Every worker, source or sink plugin class. Docs and `trellis worker list` read it. |
| `@instrumented("span.name")` | `trellis_core.instrumentation` | A span + timing log around a function; keeps the signature (ParamSpec); never wraps twice. CLI commands get it automatically. |
| `@time_machine.travel(..., tick=False)` | tests | Freezing time. `freezegun` is banned (no pydantic v2 support). |

Do not invent a decorator for something a function call states more clearly; add one only when it
removes a cross-cutting concern from many call sites (as the AOP rules of the Java side, T8).

## Workers

The framework owns delivery: source, retries with backoff, DLQ, acknowledgement, offsets,
shutdown. A worker supplies only the parts:

1. `workers/<name>/pyproject.toml` with an entry point
   `[project.entry-points."trellis.workers"] <name> = "trellis_<name>.worker:<Class>"`.
2. `config.py`: a `ConfigModel` for the `[worker]` section.
3. `worker.py`: `@plugin(...) class X(WorkerDefinition)` whose `build()` validates the config and
   returns `WorkerParts(event_types=..., action=..., filters=..., transformers=...)`.
4. The action is idempotent: wrap side effects in `inbox.transaction(event_id, correlation_id)`;
   `None` from it means duplicate. A side effect outside the database uses `event_id` as the remote
   idempotency key.
5. Raise `PermanentError` for "retrying cannot help" (goes straight to the DLQ); any other exception
   is retried with backoff. Return `False` from `act()` only for deferred work, and call
   `event.settle()` when it completes.
6. `uv sync`, then `uv run trellis worker run <name> --dry-run` and an in-memory run with `--input`.

`consumer_name` is the inbox key and the Kafka group id. Renaming it reprocesses the whole topic.

## Event models are generated

Contracts live in `../contracts/events/<eventType>/v<N>.schema.json` (with a PascalCase `title`,
which becomes the class name). Models are generated into `trellis_events/generated/` by
`../contracts/codegen/python/generate.py` (datamodel-code-generator, pinned): never edit them, and
ruff skips that folder. After changing or adding a schema, run the generator and commit the
output; CI runs it with `--check` and fails on drift. New payloads are registered automatically
(`generated/index.py`). `trellis_events.envelope.Envelope` subclasses the generated envelope to add
behaviour; do the same if a payload needs methods. A breaking change is a new version, not an edit.

## Generated API client

`trellis_sdk.generated` is generated from `../contracts/openapi/openapi.json` by
`../contracts/codegen/python/generate_sdk.py`: `models.py` (pydantic v2 models of the schemas,
datamodel-code-generator, same pinned version and options as the event models) and `resources.py`
(one class per OpenAPI tag, one typed method per operation). Never edit them; ruff skips that
folder, mypy checks it.

```python
with TrellisClient.from_env() as client:          # TRELLIS_API_URL + TRELLIS_API_TOKEN
    page = client.notice.list_notices(first=50, audience="ALL")
    view = client.notice.publish_notice(code, idempotency_key=str(uuid4()))
```

Every generated method calls `TrellisHttpClient.request`, so retries, `Retry-After`, token refresh,
correlation/trace headers and `ProblemDetail` -> `ApiError` apply; path parameters are URL-quoted;
responses are validated into the models (unknown fields are ignored, so additive API changes do not
break workers). Endpoints are exposed as properties named after the tag (`client.notice`,
`client.ops`, `client.app_config`).

When the backend API changes: `./gradlew :trellis-app:exportApiContracts` (repo root), then
`uv run python ../contracts/codegen/python/generate_sdk.py`, and commit both. CI runs the
generator with `--check` and fails on drift. Workers consume events, not REST, for anything the
backend publishes; use the client only to change state the backend owns.

## Running a worker in a container

Without `--config`, `trellis worker run <name>` reads the environment (names shared with
`docker/compose.yml`):

| Variable | Meaning |
| --- | --- |
| `TRELLIS_EVENTS_TRANSPORT` | `kafka` or `inmemory` (default). In-memory without `--input` idles until SIGTERM. |
| `TRELLIS_KAFKA_BOOTSTRAP_SERVERS` | Required for `kafka`. |
| `TRELLIS_DATABASE_URL` | Worker database (inbox + worker tables). Unset = in-memory stores. Masked in logs. |
| `TRELLIS_HEALTH_HOST` / `TRELLIS_HEALTH_PORT` | `/health/live`, `/health/ready`, `/metrics` (default `127.0.0.1:9464`). |

The health server runs for every long-running worker (not for `--input` runs, and with
`--config` only when `[health] enabled = true`). `trellis worker health <name>` exits 0 only when
`/health/ready` is 200: the poll loop is alive (heartbeat younger than `max.poll.interval.ms`), the
broker and the inbox database answer, and no shutdown is in progress. Liveness never checks
dependencies. Dev/test DDL for the Postgres tables ships as `*.dev.sql` package data; deployed
environments get their schema from the backend's Liquibase changelog.

## Adding a CLI command

One module per command group in `trellis_cli/commands/`, attached in `trellis_cli/main.py`. A
command returns an `ExitCode`, writes its result with `emit(state, data, text)` (so `--format json`
works), writes banners with `announce()` (stderr), and offers `--dry-run` if it has side effects.
Raise `TrellisError` subclasses for expected failures: `main()` maps them to exit codes.

## Testing (decision T5)

- Tests exist for **tooling and contracts only**: envelope/payload schema conformance, shared test
  vectors with Java (`contracts/testdata/*.json`), plugin loading, config redaction, CLI exit codes.
  Do not add feature tests until the owner asks.
- `tests/integration/` (marker `integration`, deselected by default) verifies infrastructure on real
  containers it starts and removes itself (free localhost port, `docker run`): inbox and counter
  store on Postgres 17, concurrent duplicates, restart, the health endpoint.
- Tests run in UTC (forced in `tests/conftest.py`) with telemetry off; freeze time with
  `time-machine`. Paths come from `tests/support.py`, never from the working directory.
- A test must be able to catch a real regression; no tests of pydantic, click or the stdlib.
