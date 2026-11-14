# Python side: workers, SDK, CLI

Status: v0.1. Code: `workers/` (uv workspace, Python 3.12, decision T14). Module rules for
contributors: `workers/AGENTS.md`. This page explains how the pieces fit and why.

## Packages

```
trellis-core     no I/O frameworks; everything else depends on it
   ^  ^  ^
   |  |  +---------- trellis-sdk      httpx client for the backend REST API
   |  +------------- trellis-events   worker framework: envelope, pipeline, inbox, transports
   |                    ^       ^
   +---- trellis-cli ---+       +---- workers/notice-stats   (sample worker, entry point plugin)
```

| Package | Owns | Optional extras |
| --- | --- | --- |
| `trellis-core` | `ConfigModel`, `env_vars`, `EnvResolver`, `SecretRegistry`, structlog setup, OTel setup, `Backoff`, `uuid7`/`stable_id`, `PluginRegistry` + `@plugin`, `@instrumented`, `Report`/`LossyList`, error hierarchy and exit codes | `otlp` |
| `trellis-events` | `Envelope` (subclass of the generated model), generated payload models + registry, contract validator, pipeline engine, `PartitionExecutor`, `OffsetTracker`, `ShutdownSignal`, inbox guards, transports, health/metrics server, `WorkerDefinition` + runner | `kafka`, `postgres` |
| `trellis-sdk` | `TrellisHttpClient` (retries, 429, token refresh), `TokenProvider`s, `ProblemDetail` errors, batching helpers, `TrellisClient` facade with the OpenAPI-generated resources (`trellis_sdk.generated`, from `contracts/openapi/openapi.json` by `contracts/codegen/python/generate_sdk.py`, drift-checked in CI) | - |
| `trellis-cli` | `trellis` command: `version`, `events validate`, `worker list`, `worker run`, `worker health`, `config show` | - |

Python workers **consume** domain events; they never publish domain events straight to Kafka.
When a worker must change state owned by the backend it calls the REST API through the SDK
(GraphQL/REST boundary, decision T12).

## Pattern catalogue

Each pattern: where it lives, and why it exists. "DataHub" marks patterns re-implemented after
reading DataHub's source (`plan/06`); nothing was copied verbatim.

| Pattern | Module | Why |
| --- | --- | --- |
| Strict config base (DataHub) | `trellis_core.config.ConfigModel` | A typo in a key fails at startup; validation errors never echo a secret; secrets are registered for masking the moment config is parsed; `model_dump_redacted()` masks by type, not by guessing from key names. |
| One env module (DataHub) | `trellis_core.env_vars` | Every `TRELLIS_*` variable has one typed, documented declaration. ruff bans `os.environ`/`os.getenv` elsewhere, so a new variable cannot hide in a library. |
| `${VAR}` with nounset (DataHub) | `trellis_core.env_resolver` | Config files reference secrets without containing them; a missing variable is an error naming it, not an empty string. Expanded values count as secrets. |
| Secret masking, fail closed (DataHub) | `trellis_core.secrets`, `trellis_core.logs` | The final rendered log line is masked as a whole, so secrets cannot escape through exception text or library logs. If masking itself fails, the line is withheld. |
| Structured JSON logs + context | `trellis_core.logs` | One JSON object per line on stderr, with `trace_id`, `correlation_id`, `event_id`, `topic/partition/offset` from `contextvars` (the Python MDC). Same key names as the Java MDC. librdkafka logs are routed through it too. |
| OpenTelemetry, off by default | `trellis_core.tracing` | Consumer spans continue the producer's `traceparent` from the envelope (survives replay and DLQ). Disabled unless `TRELLIS_OTEL_ENABLED=true`; no product telemetry exists. |
| Shared backoff formula | `trellis_core.backoff` + `contracts/testdata/backoff.json` | Exponential, full jitter, capped; identical in Java and Python, proven by shared vectors. Used by the pipeline and the HTTP client. |
| UUIDv7 / StableId | `trellis_core.ids` + `contracts/testdata/stable-id.json` | Time-ordered ids for indexes and keyset paging; content-derived ids that are equal across languages. |
| Plugin registry via entry points (DataHub) | `trellis_core.plugins.PluginRegistry` | Installing a package adds a worker; nothing registers by import side effect. Lazy import, a broken plugin is *disabled with its reason* instead of breaking startup, aliases keep renamed plugins working. DataHub's "import from cwd" is deliberately left out. |
| Metadata decorator (DataHub, adapted) | `trellis_core.plugins.plugin` | One frozen `PluginMeta` per class (name, config class, status) instead of several decorators that assign lambdas; docs and `worker list` read it. |
| Instrumentation decorator (DataHub, adapted) | `trellis_core.instrumentation.instrumented` | Span + timing with the signature kept (ParamSpec). A marker attribute, not the function name, prevents double wrapping (the bug DataHub documents). Auto-applied to every CLI command. |
| Structured report (DataHub) | `trellis_core.report` | `LiteralString` messages group identical problems; `LossyList` bounds memory while keeping the true count. |
| Four-role worker contract (DataHub actions) | `trellis_events.pipeline.roles` | `EventSource -> Filter -> Transformer -> Action`, `act() -> None/True/False`, exception = retry. Small, proven surface. |
| Retry + DLQ + ack-last (DataHub actions, fixed) | `trellis_events.pipeline.engine` | DataHub retries without backoff and writes failures to a local file; Trellis backs off, distinguishes permanent errors, dead-letters to `<eventType>.dlq` with headers, and acknowledges only after the DLQ confirms. |
| Per-partition commit watermark (new) | `trellis_events.pipeline.offsets` | Commits the lowest in-flight offset, so a deferred or failed event is never skipped by a later success (the `processed=False` hole in DataHub's Kafka source). |
| Ordered parallelism with backpressure (DataHub) | `trellis_events.pipeline.partition_executor` | Parallel across aggregate ids, strictly ordered within one; `submit` blocks at `max_pending` instead of buffering the topic in RAM. |
| Graceful shutdown (DataHub actions) | `trellis_events.pipeline.shutdown`, `trellis_cli.commands.worker` | Handlers installed before config loads (PID 1 ignores unhandled SIGTERM); `request_stop` is flag-only and signal-safe; `close` is idempotent and closes everything even if one step fails; second signal exits 130. |
| Inbox (transactional idempotent consumer) | `trellis_events.inbox` | `processed_event(consumer_name, event_id)` inserted in the same transaction as the side effect, same table and key as Java. Makes at-least-once harmless. |
| Transport abstraction | `trellis_events.transports` | `kafka` in production, `inmemory` for tests and local runs, same delivery contract (`postgres` is v0.2, T11). |
| HTTP client (DataHub) | `trellis_sdk.http` | One pooled client, default timeouts, retries only for idempotent requests (or with `Idempotency-Key`), honours `Retry-After`, refreshes tokens before expiry and once after a 401, forwards `x-correlation-id` and `traceparent`. |
| Typed API errors | `trellis_sdk.errors` | Maps RFC 9457 `ProblemDetail` (+ `code`, `correlationId`, `traceId`) to exception classes that are also `PermanentError`/`TransientError`, so a worker calling the API gets the right retry behaviour for free. |
| CLI conventions (DataHub) | `trellis_cli` | `standalone_mode=False` + explicit exit codes, `--format json` on stdout, logs on stderr, `--dry-run` for side effects, `--profile`, extra help for agents when stdout is not a TTY. |
| Models generated from contracts | `contracts/codegen/python/generate.py` -> `trellis_events.generated` | One source of truth (the JSON Schemas) for Java, Python and TS. Pinned generator, deterministic formatting, `--check` drift gate in CI; `index.py` registers every payload, so a new contract cannot be forgotten. |
| Health and metrics endpoint | `trellis_events.health`, `trellis worker health` | Liveness without dependencies; readiness = poll heartbeat fresh + broker reachable + inbox database answers + not stopping; Prometheus counters and lag. Loopback by default. The DB probe runs on a helper thread with a timeout, because a hung server would block a pooled connection forever. |
| Container mode from env | `trellis_cli.commands.worker`, `trellis_core.env_vars` | `TRELLIS_EVENTS_TRANSPORT`, `TRELLIS_KAFKA_BOOTSTRAP_SERVERS`, `TRELLIS_DATABASE_URL`, `TRELLIS_HEALTH_*`: an image needs no config file. In-memory without input idles until SIGTERM, so `compose up --wait` sees a healthy long-running process. |
| Test hygiene (DataHub) | `tests/conftest.py` | UTC forced, telemetry off, `time-machine` (freezegun banned by ruff), shared Java/Python vectors. |
| Integration verification | `tests/integration/` (`-m integration`) | Starts its own Postgres 17 container on a free port and removes it: inbox + store, concurrent duplicates, restart, health readiness flip. Coverage: `pytest --cov` writes Cobertura `coverage.xml`. |

## Decorator catalogue

The Java side uses annotations + AOP (decision T8). Python keeps the list short on purpose:

| Decorator | Kind | Effect | Java counterpart |
| --- | --- | --- | --- |
| `@plugin(name, description, config, status, capabilities)` | class metadata | Records `PluginMeta` on the class (not inherited). Read by the registry, `trellis worker list`, docs. | a marker annotation read by reflection |
| `@instrumented(name)` | behaviour | OTel span + debug timing log, outcome classified (completed / cancelled / error), idempotent. | Micrometer `@Observed` |
| `@time_machine.travel` | tests | Freeze time (UTC). | `Clock` injection |

Retry is **not** a decorator: the pipeline owns retries for events, the SDK owns retries for HTTP.
A second retry layer around either multiplies attempts (4 x 4 = 16 calls) under load.

## Worker lifecycle

```
process start
  |-- ShutdownSignal.install()                 SIGTERM/SIGINT handlers first (PID 1 rule)
  |-- read config file, expand ${VAR}           unset var -> exit 78
  |-- WorkerFileConfig + worker ConfigModel     invalid -> exit 78   (--dry-run stops here, exit 0)
  |-- PluginRegistry.get(name)                  missing/broken plugin -> exit 69
  |-- WorkerDefinition.build() -> WorkerParts   (action, filters, transformers, event types)
  |-- Pipeline(source, dlq, parts)
  |-- HealthServer.start()   /health/live, /health/ready, /metrics (if enabled)
  |
  |   +------------------- run loop ------------------------------------------------------+
  |   | for delivery in source.events():         (Kafka: poll + periodic sync commit)      |
  |   |   decode envelope       --invalid-------------------------------> fail           |
  |   |   type subscribed?      --no--> ack                                                |
  |   |   attempt 1..max: payload -> filters -> transformers -> action                   |
  |   |       ok -> ack   False -> hold (settle() later)   PermanentError -> fail        |
  |   |       other error -> sleep Backoff.delay (interruptible by stop) -> retry         |
  |   |   fail: CONTINUE -> DLQ publish -> ack   (DLQ error -> no ack, stop, exit 1)      |
  |   |         THROW    -> no ack, stop, exit 1                                           |
  |   +-------------------------- stop requested / drained / fatal ------------------------+
  |
  |-- Pipeline.close()       drain PartitionExecutor, commit watermark, close consumer, flush DLQ
  |-- (shutdown_grace_s)     /health/ready keeps answering 503 so probes notice before exit
  |-- HealthServer.stop()
  |-- WorkerParts.close()    release pools
  +-- exit 0 (clean) | 1 (failure) | 130 (second signal)
```

## Delivery semantics

| Situation | What happens | Acknowledged? | Committed offset |
| --- | --- | --- | --- |
| Action succeeds | counters `processed` | yes | advances past it once all lower offsets are done |
| Same `event_id` again | inbox returns `None`; `duplicates` | yes | advances |
| Event type not subscribed / filter rejects / transformer drops | `filtered` / `dropped` | yes | advances |
| Transient error, retries left | sleep `Backoff.delay(attempt)` then retry | not yet | held at this offset |
| `PermanentError`, invalid envelope, invalid payload, unknown `eventVersion` | DLQ (`CONTINUE`) | after DLQ confirms | advances after DLQ write |
| Retries exhausted | DLQ (`CONTINUE`) | after DLQ confirms | advances after DLQ write |
| DLQ publish fails | pipeline stops, exit 1 | **no** | held: redelivered after restart |
| `failure_mode = THROW` | pipeline stops, exit 1, no DLQ | **no** | held: redelivered after restart |
| Action returns `False` | `deferred`; later `event.settle()` | when settled | held at this offset until settled |
| Stop requested during backoff | event abandoned for now | **no** | held: redelivered after restart |
| Crash anywhere | - | - | last committed position: redelivery, the inbox absorbs it |

Guarantee: **at-least-once** delivery to the action, **effectively-once** effects for side effects
inside the inbox transaction. Order: per partition key (aggregate id) with any `concurrency`.

### Wire details shared with Java

| Item | Value |
| --- | --- |
| Envelope | `contracts/events/envelope.schema.json`; consumers are tolerant readers (unknown fields ignored) |
| Topic | `eventType` (e.g. `notice.published`); key = `partitionKey` or `aggregateId` |
| Headers | `x-event-id`, `x-event-type`, `x-event-version`, `x-correlation-id`, `traceparent` |
| DLQ topic | `<eventType>.dlq` (unparseable body: `<source topic>.dlq`); body and headers unchanged |
| DLQ headers | `x-dlq-original-topic`, `x-dlq-original-partition`, `x-dlq-original-offset`, `x-dlq-consumer`, `x-dlq-exception`, `x-dlq-error` (max 2000 chars), `x-dlq-attempts`, `x-dlq-failed-at` |
| Inbox | `processed_event(consumer_name VARCHAR(100), event_id UUID, correlation_id VARCHAR(100), processed_at TIMESTAMPTZ)`, PK `(consumer_name, event_id)`; DDL owned by the backend Liquibase changelog (dev/test copy: `trellis_events/inbox/sql/processed_event.dev.sql`) |
| Backoff defaults | base 1000 ms, multiplier 2, cap 30000 ms, 4 attempts in total, full jitter |
| Kafka consumer | `enable.auto.commit=false`, `enable.auto.offset.store=false`, `auto.offset.reset=earliest`, `group.id = consumer_name`, sync commit of the watermark every 1 s, on revoke and on close |
| DLQ producer | `enable.idempotence=true`, `acks=all`, synchronous flush per record |

## Health endpoint

| Path | 200 when | Body |
| --- | --- | --- |
| `/health/live` | the process serves HTTP | `{"status":"UP"}` |
| `/health/ready` | `source` (poll heartbeat younger than `max.poll.interval.ms` and broker metadata reachable, refreshed every `health_refresh_ms` on the poll thread), `worker` (e.g. `SELECT 1` on the inbox database within 2 s), `running` (no shutdown requested) are all UP; else 503 | `{"status":"UP|DOWN","checks":{...}}` |
| `/metrics` | always | `trellis_worker_events_{received,processed,duplicates,filtered,dropped,deferred,retries,dead_lettered,failed}_total`, `trellis_worker_ready`, `trellis_consumer_lag{topic,partition}` |

Lag = high watermark minus this consumer's position (in-flight watermark, else fetch position,
else committed offset). Off with `--config` unless `[health] enabled = true`; on by default in
container mode (no `--config`, no `--input`) at `TRELLIS_HEALTH_HOST:TRELLIS_HEALTH_PORT`
(`127.0.0.1:9464`).

## Known limits (v0.1)

- One pipeline per process; scale with replicas in one consumer group.
- Lag and broker reachability are refreshed on the poll thread, so they are as stale as the
  slowest event being processed (bounded by `max.poll.interval.ms`).
- Every `${VAR}` value is masked in logs, including harmless ones such as the Kafka bootstrap
  address. This is the safe default; container mode reads `TRELLIS_KAFKA_BOOTSTRAP_SERVERS` without
  `${}` expansion, so it is not masked there.
- Generated models are Python only; TS/Java generation lives with those modules.
