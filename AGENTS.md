# Trellis

Canonical guide for every agent (Claude Code, Cursor, Codex, ...) and for humans. `CLAUDE.md` only
imports this file. Module rules live in the module's own `AGENTS.md` and are not repeated here.

## What this repo is

`trellis-backend` is the Java backend of **Trellis**, a reusable source base modelled on DataHub's
engineering standards. It holds **no business domain**: only the platform skeleton and one tiny sample
module (`notice`) that shows every layer end to end. Products are generated from it; the token
`trellis` / `io.trellis` / `TRELLIS_` is what the future `init` script renames.

It also owns the pieces the other two repos depend on: the **contracts** (event JSON Schemas, the
exported OpenAPI document and GraphQL schema, published as client packages) and the **local stack**
(compose, dev CLI).

## Sibling repos (decision T21)

| Repo | What | Depends on this one through |
| --- | --- | --- |
| `trellis-web` | Next.js frontend, also the BFF | the published npm contract package |
| `trellis-ai` | Python: RAG, Kafka workers, SDK, CLI | the published PyPI contract package, and the events this backend emits |

## Repo map

| Path | What | Rules / docs |
| --- | --- | --- |
| `trellis-common/` | plain Java, no Spring: error model, context, ids, paging, AOP annotations | this file |
| `trellis-platform/` | Spring infrastructure: persistence, authorization, observability, AOP aspects | this file, `docs/architecture/backend.md` |
| `trellis-platform-events/` | outbox, relay, inbox, transports, dead letters | same |
| `trellis-platform-web/` | REST, GraphQL, OpenAPI, security filter chain | same |
| `trellis-identity/` | native account/password/session lifecycle; Keycloak remains provider two | `docs/adr/0003-native-identity-and-oidc.md` |
| `trellis-notice/` | the sample business module - copy it to start a real one | `trellis-notice/README.md` |
| `trellis-learning-content/` | business module grouping the `course`, `lesson`, `authoring`, `publishing` features (decision T26) | `trellis-learning-content/README.md` |
| `trellis-app/` | the deployable that assembles everything | this file |
| `contracts/` | event schemas, OpenAPI, GraphQL schema; published as packages | `docs/architecture/contracts.md` |
| `build-logic/docker/`, `build-logic/scripts/dev/` | images and the local stack (`build-logic/scripts/dev/trellis-dev.sh --help`) | `docs/architecture/delivery.md` |
| `build-logic/ci/` | GitLab CI pipeline, Trivy config | `docs/architecture/delivery.md` |
| `docs/` | ADRs (`docs/adr/`), architecture, `docs/how/breaking-changes.md` | - |
| `.agent-skills/`, `.claude/` | portable agent skills; Claude Code settings and hooks | `.agent-skills/README.md` |

Directory names are the Gradle project name without the `trellis-` prefix (decision T25), so
`trellis-platform-events/` is `:trellis-platform-events`. Tool versions are pinned in `mise.toml`.

## Essential commands

Adding a business module: `docs/guide/new-module.md`. Using `trellis-common` (annotations,
utilities, where new shared code goes): `docs/guide/trellis-common.md`. How the three repos talk to
each other: `docs/architecture/cross-repo.md`.


Full run guide for all three repos: `docs/guide/running.md`.

See "Commands" below for the full list. The short version:

```bash
./gradlew build                      # compile, test, ArchUnit, contract check
./gradlew spotlessApply              # format Java and Kotlin DSL
build-logic/scripts/dev/trellis-dev.sh up --profile full
./gradlew :trellis-app:bootRun       # API on :8080, actuator on :8081
```

## Code standards (all languages)

- **Comments explain why**: an external constraint, a warning, a reference. Never restate the code.
  - Bad: `// increment retry count`
  - Good: `// The proxy drops idle sockets at 60 s, so retry once before surfacing the error.`
- **No magic string or number literals** for domain values, codes, statuses, header names, storage keys.
  Put them in a named constant or enum owned by the module. Exempt: log text, test fixtures, and the path
  on a controller's own mapping annotation - there the literal *is* the declaration, and lifting it into a
  constant only moves the route one indirection away from the method that serves it.
- **Type everything.** No raw types, no unchecked casts; a value object beats a bare `String`.
- **Generated files are never hand-edited.** Change the source (schema, contract, script) and regenerate.
  Generated output carries a header saying so.
- Validation and authorization live below the API layer; a hidden button is never authorization.

## Testing policy (decision T5)

- Tests exist **only for the platform itself**: ArchUnit rules, the context-load test, the config
  classification guardrail, aspect ordering, contract and shared-vector conformance, and the one
  reference flow (`NoticeFlowIT`).
- **Do not write feature tests unless the owner asks.** This is a deliberate, temporary rule pending the
  owner's decision (T5).
- When tests are written: a test must be able to catch a real regression. Do not test framework
  behaviour, exact error message wording, or that an annotation exists.

## Commits and PRs

- Conventional Commits: `type(scope): description` (`feat(notice): add archive endpoint`).
  Types: `feat`, `fix`, `refactor`, `perf`, `docs`, `test`, `build`, `ci`, `chore`.
- Breaking changes: `!` after the type and an entry in `docs/how/breaking-changes.md`, written for
  someone who does not read code.
- **Never use `--no-verify`.** A failing hook is a signal to stop and fix, not to skip. Report it and ask.
  `.claude/hooks/deny-git-no-verify.sh` enforces this for Claude Code.

## Confidentiality

No real customer names, hostnames, database or table names from real systems, or internal ticket IDs
in code, tests, fixtures, comments or commit messages. Use `example.test` domains and neutral sample data.

## Architecture decisions

Recorded as ADRs in `docs/adr/` (template in `0001`). A decision that changes how modules talk to each
other, or a toolchain choice, gets an ADR.

---

# Java rules

Design rationale, diagrams and the pattern catalogue: `docs/architecture/backend.md`.

## Stack

Java 21 (toolchain), Spring Boot 4.0.9 (Spring Framework 7, Jackson 3, Spring Security 7), Gradle 9.9.1 Kotlin
DSL with convention plugins in `build-logic/` and versions in `gradle/libs.versions.toml`, PostgreSQL 17 via Spring Data JPA for business aggregates and
`JdbcClient` for keyset pages and platform stores (ADR 0004), Liquibase, Kafka (Spring Kafka 4), Micrometer + Prometheus, OpenTelemetry through the
Micrometer bridge, Bucket4j, ArchUnit, Testcontainers, Spring for GraphQL 2.0 (graphql-java 25), springdoc-openapi
3.0.3 (the 3.0 line is the one built on Boot 4.0), Lombok (compile-time only).

## Commands

Always run with JDK 21 (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)` on macOS; `mise` pins it too).

| Command                                   | What                                                                 |
| ----------------------------------------- | -------------------------------------------------------------------- |
| `./gradlew build`                         | Compile (javac `-Werror`), Spotless check, all tests (needs Docker)  |
| `./gradlew lintCheck` / `lintFix`         | Spotless (Google Java Format, ktlint for `*.gradle.kts`) check/fix |
| `./gradlew format`                        | Alias of `lintFix`                                                   |
| `./gradlew test jacocoTestReport`         | Tests + XML coverage in `build/reports/jacoco/test/` (CI)            |
| `./gradlew :trellis-app:bootRun`          | Run the app (profile `dev` by default)                               |
| `./gradlew :trellis-app:bootJar`          | The runnable jar used by `build-logic/docker/backend.Dockerfile`                 |
| `./gradlew :trellis-app:exportApiContracts` | Boots the app (test profile, Testcontainers) and writes `contracts/openapi/openapi.json` + `contracts/graphql/schema.graphqls` |
| `./gradlew :trellis-app:checkApiContracts`  | Same, but fails when the committed contracts are stale (part of `check`/`build`) |
| `docker compose -f build-logic/docker/compose.yml --profile minimal up -d --wait` | Postgres on 15432 for `bootRun` |
| `docker compose -f build-logic/docker/compose.yml --profile full up -d --wait postgres kafka` | + Kafka on 19092 |

App modes (Spring profiles added to `dev`/`prod`): `setup` (Liquibase + Kafka topics + S3 bucket, exit 0),
`migrate` (Liquibase + `MigrationStep`s, exit 0/1). Dev-only round trip: `POST /api/dev/outbox/ping`, then
`GET /api/dev/outbox/received`. Actuator is on port 8081 (`/actuator/health/{liveness,readiness}`,
`/actuator/prometheus`).

## Module map

| Module                           | Package                          | Depends on                 | Owns                                                                                     |
| -------------------------------- | -------------------------------- | -------------------------- | ---------------------------------------------------------------------------------------- |
| `trellis-common`                 | `io.trellis.common`              | nothing (no Spring/Jackson) | error model, `RequestContext`/`Actor`, `UuidV7`/`StableId`, `Backoff`, `Cursor`, `Violations`, `Permission`, `HttpHeaderNames`/`LogKeys`, the eight AOP annotations (`aop/`); test fixtures: `MutableClock` |
| `trellis-platform`               | `io.trellis.platform.{persistence,security,observability,aspect}` | common | base Spring infrastructure every deployable needs: `JdbcSupport`, `RetryingTransactions`, `KeysetQuery`, `MigrationStep` + runner, trace accessor and context-propagating `TaskDecorator`, `ActorResolver`, `AuthorizationService`, `SensitiveProperties`, the aspects + stores behind the annotations, `AspectOrder`, audit/idempotency tables. No servlet, no GraphQL |
| `trellis-platform-events`        | `io.trellis.platform.events`     | common, platform           | outbox, relay, transports, consumer framework, DLQ replay, read-model rebuild SPI         |
| `trellis-platform-web`           | `io.trellis.platform.web`        | common, platform           | the HTTP edge: RFC 7807 errors (`ProblemMapper`s), correlation id, request log, `RequestContext` argument resolver, the security filter chain + `JwtDecoder` + `@PublicEndpoint` (`web.security`), Spring for GraphQL wiring (`web.graphql`: context, error mapping, depth/complexity limits, `DateTime` scalar, `Query.ping`) |
| `trellis-identity`               | `io.trellis.identity`            | common, platform, web      | native accounts, adaptive password hashes, opaque sessions, verify/reset tokens and SMTP account mail; Keycloak JWT remains provider two |
| `trellis-notice`                 | `io.trellis.notice`              | common, platform, events, web | the sample business module; copy it (`trellis-notice/README.md`)   |
| `trellis-learning-content`       | `io.trellis.learningcontent`     | common, platform, events, web | course content: the `course`, `lesson`, `authoring` and `publishing` features share one lifecycle, so they share one module (decision T26) |
| `trellis-app`                    | `io.trellis.app`                 | everything                 | `TrellisApplication`, `application*.yml`, Liquibase master, `FeatureFlags`, app-config, ops, setup, dev tools, OpenAPI configuration |

Every platform module registers itself through `META-INF/spring/...AutoConfiguration.imports` (one entry per
concern: persistence, migration, observability, security, aspects; web, web security, GraphQL); adding the
Gradle dependency is all an app needs. Web-only beans stay `@ConditionalOnWebApplication(SERVLET)` so a
`setup`/`migrate` or consumer run never instantiates them. Component scanning covers `io.trellis.app` only; a business module
registers a `<Name>Module` auto-configuration that scans its own `application`, `infrastructure` and `web`
packages.

## How big is a business module (decision T26)

A module is **a group of features with one lifecycle**, not one aggregate. Keep the three levels apart:

| Level | Example | Its own Gradle module? |
| --- | --- | --- |
| business domain | Learning Management | no |
| bounded context | Learning Content, Assessment, Billing | yes, when the boundary is real |
| feature / use case | Create Course, Publish Lesson, Submit Quiz | no - a package and a class |

Thirty use cases do not mean thirty modules. **The default is to add a feature to an existing module**; a new
module needs condition T18 *and* a short ADR answering: what business and data would it own that the current
module should not; what is its public API and who uses it; what actually goes wrong with a plain Java package;
does splitting create a cycle or make one use case span more modules; is the extra build and maintenance cost
worth it. `Course` and `Lesson` share the content lifecycle, so they share a module. Billing is a module
because its invariants, sensitive data, audit duties and payment-provider integration are genuinely different.

## Layering of a business module

A business module is a Gradle module `<name>/` at the repo root (Gradle path `:trellis-<name>`); `trellis-notice` is the reference to copy
(`trellis-notice/README.md`). Every layer has fixed sub-packages (decision T17); a sub-package exists once it
holds a class, and the layer rules are enforced by ArchUnit rather than by marker files:

```text
io.trellis.<name>
  <Name>Module      auto-configuration that scans application/, infrastructure/, web/
  api/              no Spring
    dto/            wire vocabulary (enums) shared with clients and other modules
    event/          event payload records, one per contract version
    security/       <Name>Permissions (@PermissionCatalog)
    error/          <Name>ErrorCode enum, exception factories
  domain/           no Spring, no SQL (ArchUnit)
    model/          aggregate + entities: immutable, transitions return the next state
    value/          value objects (ids such as reference codes)
    policy/         pure business rules over domain types (e.g. visibility)
  application/
    usecase/<capability>/   one class per use case, <Verb><Noun>UseCase, one public execute
    support/        collaborators several use cases share (loaders, guards); no AOP annotations
    port/           repository interfaces, filters; implemented by infrastructure
    view/           read models returned by use cases + the aggregate -> view mapper
    validation/     CommandValidators run through ValidationPipeline
  infrastructure/
    persistence/    JdbcClient repositories
    messaging/      EventHandlers, ReadModelRebuilders
    client/         external clients (only when there is one)
  web/
    rest/           @RestController, one endpoint per use case
      dto/<capability>/   request and response records, validated for shape only
    graphql/        @Controller with @QueryMapping/@SchemaMapping/@BatchMapping
resources: db/changelog/<name>/NNN-*.sql, graphql/<name>/*.graphqls, META-INF/spring/...imports
```

**A module with more than one feature** inserts one feature level into `domain/` and `application/` only -
`domain/course/model/`, `application/lesson/command/` - and keeps the T17 sub-packages inside it.
`api/`, `infrastructure/` and `web/` stay flat: they are the module's one published surface, one set of
adapters and one set of entry points. `trellis-learning-content` is the example; `trellis-notice` has one
feature and so has no feature level.

Multi-word module names use hyphens in the directory and the Gradle path, and no hyphens in the package:
`trellis-learning-content/` is `:trellis-learning-content` and `io.trellis.learningcontent`. **Never nest the
package** (`io.trellis.learning.content`): ArchUnit tells business modules apart by the single segment after
`io.trellis`, so a nested name would be read as part of a `learning` module. The auto-configuration class is
named after the module (`LearningContentModule`), while tables, permissions, audit actions and metrics are
named after the aggregate (`course`, `course:read`, `course.created`).

Use-case classes (decision T16): **one class per use case**, named `<Verb><Noun>UseCase` (`LoginUseCase`,
`PublishNoticeUseCase`, `ListCoursesUseCase`), with a single public `execute` method. They live in
`application/usecase/<capability>/` - `authoring`, `publishing`, `reading`, `authentication`, whatever the
module's capabilities are - so a module with twenty use cases still reads as a handful of groups. These classes
are where `@Transactional` and the AOP annotations live.

A class that several use cases share is a collaborator, not a use case: it goes in `application/support/`, is
named after what it owns (`ActionTokens`, `NoticeLoader`), and never carries the AOP annotations. A single
`<Aggregate>Service`, or one `<Aggregate>Commands` holding every write, is what this rule exists to prevent:
the dependencies of five use cases pile into one constructor and nothing says which method needs which.

Rules: `web -> application -> domain`; `infrastructure` implements ports declared in `application/port`; nobody
depends on `web`; `web` never touches repositories, `JdbcClient` or the outbox (ArchUnit:
`businessDomainIsFrameworkFree`, `businessApplicationDoesNotDependOnAdapters`, `businessWebStaysThin`,
`nothingDependsOnBusinessWeb`). Controllers (REST and GraphQL) never hold logic, never authorize, never open
transactions. Validation of business rules runs in the application layer through `ValidationPipeline`; Bean
Validation on request DTOs is only for shape (and `@NotNull` on view records marks required OpenAPI fields).

## REST, GraphQL and generated clients (decision T12)

- **REST** (`web/rest`) is for commands, integrations, workers and ops: RFC 7807 errors, `ETag`/`If-Match` on
  updates (`VersionETags`), `Idempotency-Key` on retried creates/sends, keyset pages `{items, nextCursor,
  hasNext}` with `first`/`after`. springdoc documents it at `/v3/api-docs` (on in `dev`/`test`); the document gets
  a `Problem` schema as every operation's `default` response and tags named after the controller's resource.
- **GraphQL** (`web/graphql`, `POST /api/graphql`) is for UI read models. Schema files per module under
  `src/main/resources/graphql/<module>/*.graphqls` (`extend type Query`; `DateTime` and `Query.ping` come from
  `trellis-platform-web`, package `web.graphql`). Take `@ContextValue(GraphQlContextKeys.REQUEST_CONTEXT) RequestContext ctx` and call
  the same application services as REST: authorization stays there, never only in a resolver. Fields that would
  query per parent use `@BatchMapping` (one call per request). Errors carry `extensions.code`
  (`GraphQlErrorCode`), `errorCode` (the module code), `correlationId`, `traceId`; 5xx messages are generic.
  Limits: `trellis.graphql.max-depth` (15), `max-complexity` (200); introspection and GraphiQL only in `dev`.
- **Contracts** are generated, committed and drift-checked: `./gradlew :trellis-app:exportApiContracts` writes
  `contracts/openapi/openapi.json` and `contracts/graphql/schema.graphqls`; `checkApiContracts` runs in `check`.
  After changing an endpoint or schema: export, then `cd web && pnpm codegen` and
  `cd workers && uv run python ../contracts/codegen/python/generate_sdk.py`, and commit all of it.

## Code standards (Java)

- **Explicit context.** Application-service methods take `RequestContext ctx` as the first parameter.
  Controllers get it as a method argument (resolver); consumers get it from the dispatcher. Never read
  `AmbientRequestContext` in business code.
- **Time** comes from the injected `java.time.Clock` (or `ctx.requestTime()`); `Instant.now()` and friends are
  banned by ArchUnit.
- **No magic strings.** Header names: `HttpHeaderNames`; MDC keys: `LogKeys`; event headers: `EventHeaders`;
  problem fields/types: `ProblemFields`/`ProblemTypes`; error codes: an `ErrorCode` enum per module;
  permissions: a `@PermissionCatalog` class; event types: constants next to the payload record; the
  configuration root (`trellis.`) tested at runtime: `ConfigKeys`; SQL: a `private static final String` text
  block next to its use. Exempt, because there the literal is the data and a constant would only hide it: a
  properties class naming its own prefix, the key patterns catalogued in `SensitiveProperties`, and the path
  on a controller's mapping annotation.
- **Lombok, sparingly.** It is on every module's annotation-processor path, but it earns its place only
  where a record cannot help: `@Slf4j` for a logger, `@RequiredArgsConstructor` for a Spring bean's final
  dependencies, `@Builder` for a value with many fields. Plain data stays a `record`. `@Data` and `@Value`
  are rejected by `lombok.config`: they decide equality and `toString` for you, which a record does
  deliberately and a mutable entity should not do at all. Lombok is `compileOnly`, so it never reaches the
  runtime classpath, and `lombok.addLombokGeneratedAnnotation` keeps generated members out of coverage.
  It needs JDK 21 - it has broken on newer JDKs.
- **SQL is always native and always visible** (ADR 0004). Business aggregates use Spring Data JPA, where every
  `@Query` is `nativeQuery = true`; JPQL is not used, so the text in the file is the text the database runs.
  Keyset pages, `GrantPredicate` and the platform's own stores stay on `JdbcClient` with named parameters.
  Entities live only in `..infrastructure.persistence..` (ArchUnit), carry explicit `@Table(name)` and
  `@Column(name)`, use `@Enumerated(EnumType.STRING)`, and never relate to another aggregate. JSONB goes in as
  `CAST(:x AS jsonb)`. Identifiers that must be spliced (ORDER BY) pass `JdbcSupport.identifier`.
- **Configuration** uses plain classes with Spring field `@Value`; every key appears in
  `application.yml` as `${TRELLIS_*:default}` with a comment, and is classified in `SensitiveProperties`
  (guardrail test). Use typed values and keep defaults in the placeholders. Named conditions (`@ConditionalOnKafkaTransport`) instead of
  repeated `@ConditionalOnProperty` strings.
  Put each `@Value` on its own line immediately above its field, with a blank line between properties.
  Use comma-separated scalar values for list settings so Spring `@Value` can convert them to typed lists.
- **Errors**: throw a `TrellisException` subclass with an `ErrorCode`; never build HTTP responses for errors in
  controllers. New framework exceptions are mapped by adding a `ProblemMapper` bean, never a second
  `@ControllerAdvice`.
- **Async**: inject the `applicationTaskExecutor` (already context-propagating) or wrap your executor with
  `ContextPropagation.wrap`. `CompletableFuture.runAsync/supplyAsync` without an executor is banned.
- javac runs with `-Xlint:all -Werror`; fix warnings, do not suppress them without a comment saying why.

## Transactions and events

- `@Transactional` goes on application-service methods, not on repositories or controllers.
- `OutboxWriter.append(ctx, message)` is `Propagation.MANDATORY`: call it inside the business transaction, in
  the same method that changes the data. Same for `InboxGuard` and `AuditRecorder`. Never publish to Kafka
  directly from business code.
- Optimistic concurrency: a `version` column, `UPDATE ... WHERE id = :id AND version = :v`, then
  `JdbcSupport.requireUpdated`; wrap read-modify-write in `RetryingTransactions` when retrying makes sense.

## AOP annotations (decision T8)

Put them only on public methods of `@Service` beans in an `..application..` package (ArchUnit). Never call an
annotated method through `this` from the same class (ArchUnit detects it): the proxy is skipped and the aspect
silently does nothing. Data-dependent authorization ("only the author") is an explicit
`authorizationService.require(...)` call, not an annotation.

| Order (outer first) | Annotation               | Use for                                                   | Not for |
| ------------------- | ------------------------ | --------------------------------------------------------- | ------- |
| 1                   | `@LogExecution`          | timing/outcome of operator-relevant operations            | metrics (use `@Observed`), debugging |
| 2                   | `@RequiresFeature`         | releasing a whole operation behind a typed flag           | per-user experiments, branches inside a method |
| 3                   | `@RateLimited`           | expensive/abusable operations, per actor by default       | global API limits (gateway/BFF) |
| 4                   | `@RequiresPermission`    | "who may call this" rules                                 | rules that depend on loaded data |
| 5                   | `@Idempotent`            | client-retried creates/sends (`Idempotency-Key`)          | naturally idempotent PUT/upsert, reads |
| 6                   | `@DistributedLock`       | serialising work row locks cannot cover                   | single-row updates (use `version`) |
| 7                   | `@Transactional`         | every state-changing use case                             | controllers, repositories |
| 8                   | `@Audited`               | state changes an auditor must reconstruct (needs 7)       | reads, failures |
| 9 (innermost)       | `@Observed` (Micrometer) | metrics + span of a use case                              | - |

Why this order: see `AspectOrder` Javadoc and `docs/architecture/backend.md`. Changing it is a breaking
decision; `AspectOrderingIT` pins it.

## How to add...

- **A feature** (the common case, decision T26): a feature package under `domain/` and `application/` of the
  module that owns that lifecycle, plus its classes in the module's existing `api/`, `infrastructure/` and
  `web/`. No Gradle module, no wiring.
- **A module** (only when the ADR of T26 says so): `build-logic/scripts/dev/new-module.sh --slug <slug>
  --name <aggregate> --features <a,b,c>`, which writes the module and its wiring
  (`docs/guide/new-module.md`). By hand: copy `trellis-notice/` and follow its README ("How to copy this
  module") - add it to `settings.gradle.kts` and as a dependency of `trellis-app`, keep the T17 sub-packages,
  permissions in `api/security/<Name>Permissions`, and the roles that hold them in a
  `db/changelog/<module>/NNN-<name>-roles.sql` changeset (role to permission is data in `role_permission`,
  never a configuration property).
- **An endpoint**: a thin controller in `web/rest` taking `RequestContext ctx` and calling an application
  use-case class. Unauthenticated only with `@PublicEndpoint` (rare). Errors are exceptions with an `ErrorCode`.
  Then export the contracts and regenerate the clients (see above).
- **A GraphQL field**: add it to the module's `.graphqls`, map it in `web/graphql` (`@BatchMapping` for anything
  loaded per parent), export the contracts, `cd web && pnpm codegen`.
- **An event**: write `contracts/events/<type>/v1.schema.json` first; add a payload record and the event-type
  constant; call `outbox.append(ctx, OutboxMessage.of(TYPE, "Aggregate", id, payload))` inside the
  transaction; add the type to `trellis.setup.topics`. Consumers implement `EventHandler<T>` with a unique,
  stable `consumerName()`. Breaking payload change: new version, publish both during migration.
- **A migration**: schema change = new formatted-SQL file under the owning module's
  `src/main/resources/db/changelog/<module>/NNN-<change>.sql`, included at the end of the shared `db/changelog/db.changelog-schema.yaml` (each environment has its own master that includes it);
  never edit an applied changeset. Data backfill = a `MigrationStep` bean (resumable batches, checkpoint),
  `blocking()` if the new code cannot serve without it; run with the `migrate` profile.
- **A config property**: field in a properties record, key + comment in `application.yml`, classification in
  `SensitiveProperties` (the build fails otherwise).
- **A feature flag**: component in `FeatureFlags` with owner and sunset in `application.yml`; if the browser
  needs it, `client-visible: true` and the same key in `web/src/shared/lib/featureFlags.ts`.

## Testing (decision T5)

Only trellis-platform/tooling tests exist and new ones need a reason: `ArchitectureTest` (ArchUnit rules),
`ContextLoadTest`, `ConfigClassificationTest` (never disable), `AspectOrderingIT`, `EnvelopeContractTest`
(serialised envelope vs `contracts/events/envelope.schema.json`), `SharedVectorsTest` (Java must match
`contracts/testdata/*.json`, shared with Python), `NoticeFlowIT` (the template's reference flow: REST publish ->
outbox -> in-memory relay -> projection exactly once, idempotent replay, STAFF 404, GraphQL stats; real RS256
tokens from a test key via `@TestBean JwtDecoder`) and `ApiContractsExport` (tooling, tag `api-contracts`,
excluded from `test`; run by `exportApiContracts`/`checkApiContracts`). Do not write feature tests unless the owner asks. Tests that
need a database use `PostgresTestcontainer` (Docker required).
