# Trellis backend review rules (Java)

Rule shape adapted from DataHub (https://github.com/datahub-project/datahub), file
`.cursor/BUGBOT.md`, commit `1415bedd`. Copyright 2015 LinkedIn Corp. and DataHub contributors,
Apache License 2.0. **Only the "if X, then flag it, with this title" format is taken; every rule
below is Trellis's own**, sourced from `AGENTS.md`, `docs/architecture/backend.md` and
`docs/guide/java-testing-conventions.md`. See NOTICE.

Each rule: the trigger, what to say, the title to file it under. Prefer correctness, authorization
and broken follow-through over style — Spotless owns formatting, ArchUnit owns the boundaries.

---

## Context and time

If an application-service method does not take `RequestContext ctx` as its **first** parameter,
or business code reads `AmbientRequestContext`, then:

- Flag it. Context is passed explicitly; the ambient holder exists for infrastructure only.
- Title: "Context must be explicit"
- Severity: high.

If any code outside tests calls `Instant.now()`, `LocalDate.now()`, `System.currentTimeMillis()`
or `Clock.systemUTC()`, then:

- Flag it: time comes from the injected `java.time.Clock` or `ctx.requestTime()`.
- Title: "Time from the injected Clock"
- ArchUnit already fails this, so file it only when `./gradlew build` was not run.

---

## Magic strings

If a domain value, error code, status, header name, route, MDC key, event type, permission or
SQL fragment appears as an inline literal, then:

- Flag it and name the owner it belongs in: `HttpHeaderNames` (headers), `LogKeys` (MDC),
  `EventHeaders` (event headers), `ProblemFields` / `ProblemTypes` (problem documents), the
  module's `ErrorCode` enum (error codes), its `@PermissionCatalog` class (permissions), a
  constant next to the payload record (event types), a `private static final String` text block
  next to its use (SQL).
- Exempt: log text and test fixtures.
- Title: "No magic strings"
- Severity: medium.

---

## Layering

If `web/` touches a repository, `JdbcClient` or the outbox, or holds an `if` that is a business
rule, or authorizes, or opens a transaction, then:

- Blocking. Controllers (REST and GraphQL) read the request, call a use-case class and return.
- Title: "Controller is not thin"

If `domain/` imports Spring, Jackson or SQL, or `application/` imports an adapter
(`infrastructure/`, `web/`), or anything imports a module's `web/`, then:

- Blocking: `web -> application -> domain`, and `infrastructure` implements ports declared in
  `application/port/`. ArchUnit rules: `businessDomainIsFrameworkFree`,
  `businessApplicationDoesNotDependOnAdapters`, `businessWebStaysThin`,
  `nothingDependsOnBusinessWeb`.
- Title: "Layer boundary"

If a business module gains a class named `<Aggregate>Service`, or one class accumulates both
writes and reads, then:

- Flag it: each operation has a `<Verb><Noun>UseCase` with one public `execute`, grouped by
  capability. Do not accumulate writes in `<Aggregate>Commands` or reads in `<Aggregate>Queries`.
  Shared collaborators belong in `application/support` (decision T16).
- Title: "Use-case class naming"

If a new file crosses the module/layer roots or ignores the existing capability layout, then:

- Name its home using AGENTS.md's layout: `api/{dto,event,security,error}`,
  `application/{usecase,support,port,view,validation}`, domain types grouped by actual feature,
  `infrastructure/{persistence,messaging,client}`, `web/{rest,graphql}`. Persistence may group
  adapters/entities by capability; do not require empty `policy` folders.
- Title: "Module sub-package layout"

---

## Use-case classes

If a use case accepts a validator list solely to construct its own `ValidationPipeline`, then:

- Assemble the typed pipeline in module configuration and inject that collaborator. Use
  `@RequiredArgsConstructor` for assignment-only wiring. Preserve validator discovery, ordering,
  applicability, aggregation and the position of validation in the transaction/workflow.
- Title: "Validation assembly belongs in module configuration"
- Severity: medium.

If an application service/helper constructor streams or groups dependencies to build a lookup
registry, then:

- Move assembly to configuration/factory methods and inject the prepared registry. Keep the
  constructor assignment-only, using `@RequiredArgsConstructor` where appropriate. Preserve key
  ownership, duplicate detection and immutability; Spring's automatic bean-name-keyed map is not
  a replacement for a domain-keyed registry.
- Title: "Registry assembly belongs in configuration"
- Severity: medium.

If an application class holds more than one use case, or a controller holds a rule, then:

- One class per use case, named `<Verb><Noun>UseCase`, with a single public `execute`, under
  `application/usecase/<capability>/` (decision T16). A shared helper is a collaborator in
  `application/support/`, named after what it owns, and carries no AOP annotation.
- Request and response records live in `web/rest/dto/<capability>/` and validate shape only.
- Title: "Use-case class per use case"

---

## Persistence

If a repository mixes styles or hides its SQL, then:

- Business aggregates are mapped with Spring Data JPA, and **every** `@Query` is
  `nativeQuery = true`: the text in the file is the text the database runs (ADR 0004).
- `JdbcClient` stays for keyset pages, `GrantPredicate` and the platform's own stores. A query that
  is neither of those and is written by hand is a finding.
- An entity needs an explicit `@Table(name = ...)` and an explicit `@Column(name = ...)` on every
  field, `@Enumerated(EnumType.STRING)`, and no `@OneToMany`/`@ManyToOne` across aggregates.
- A native `@Modifying` statement that can run while an entity of the same table is loaded needs
  `flushAutomatically` and `clearAutomatically`; without them the persistence context goes stale.
- New JPA root IDs are generated Long values and null before repository save; use the saved
  aggregate. Shared password PKs use Persistable lifecycle tracking. Follow ADR 0004 for sequence,
  precise wire IDs and technical UUID exceptions. Do not replace guarded updates/locks with save.
- Title: "Persistence style"

---

## Transactions, outbox and concurrency

If `@Transactional` sits on a controller, a repository, a private method or a `domain/` class,
then:

- Blocking: it belongs on the public method of the application use-case class.
- Title: "@Transactional on the wrong method"

If an annotated method of the same class is called through `this`, then:

- Blocking. The proxy is skipped and the aspect — transaction, audit, idempotency, lock — does
  nothing, silently. Split the method out or inject the bean.
- Title: "AOP self-invocation"

If `OutboxWriter.append`, `InboxGuard` or `AuditRecorder` is called outside the business
transaction, or business code publishes to Kafka directly, then:

- Blocking. `append` is `Propagation.MANDATORY`: it runs in the same method that changes the data.
- Title: "Outbox outside the transaction"

If an `UPDATE` of a versioned row omits `AND version = :v`, or its result is not passed through
`JdbcSupport.requireUpdated`, then:

- Flag it: a lost update is silent. Wrap read-modify-write in `RetryingTransactions` when a retry
  can succeed.
- Title: "Optimistic concurrency not enforced"
- Severity: blocking.

If a new event type is added without a topic in `trellis.setup.topics`, or a consumer's
`consumerName()` changes, then:

- Flag it: a missing topic fails at runtime; a renamed consumer name reprocesses the topic from
  the beginning and voids the inbox keys.
- Title: "Event wiring follow-through"

---

## AOP annotations

For `@RateLimited`, require a public controller method in `..web..`.
For the other annotations from `trellis-common`'s `aop/`, if one sits on a non-public method, on a class outside
an `..application..` package, or on something that is not a `@Service` bean, then:

- Flag it (ArchUnit enforces it): the annotations work only there.
- Title: "AOP annotation placement"

If `@RequiresPermission` is used for a rule that depends on loaded data ("only the author", "only
their own tenant"), then:

- Flag it: data-dependent authorization is an explicit `authorizationService.require(...)` call.
  An annotation answers "who may call this", not "may they touch this row".
- Title: "Data-dependent authorization needs a call, not an annotation"

If `@Audited` appears without `@Transactional`, or the aspect order in `AspectOrder` is changed,
then:

- Flag it. `@Audited` records inside the transaction it audits; the order is pinned by
  `AspectOrderingTest` and changing it is a breaking decision.
- Title: "Aspect order"

---

## Native SQL and paging

If a new grant bypasses the approved target contract or treats every external subject as a native
user, then:

- Validate in the application layer before persistence: native USER existence, correctly typed
  encoded external IDs, global `(*, *)`, or a registered existing resource. Do not couple a grant
  request to an IdP call. Scope IDs must match the consuming authorization checks.
- Title: "Grant target contract"
- Severity: high.

If an identity migration guesses issuer provenance, rewrites all matching raw IDs without reviewed
row selection, or silently merges conflicting grants, then:

- Use the explicit-manifest preflight/dry-run protocol in `docs/guide/security-remediation.md`.
  Preserve native rows and audit history; reconcile events/idempotency data and bootstrap access
  separately. A passing development fixture is not production migration evidence.
- Title: "Reviewed identity migration"
- Severity: blocking.

If JSONB is bound without `CAST(:x AS jsonb)`, or an identifier is spliced into SQL without
`JdbcSupport.identifier` (ORDER BY), then:

- Flag it: the first fails at runtime, the second is an injection.
- Title: "Unsafe SQL construction"
- Severity: blocking for the splice.

If a list endpoint returns offset pages for a new resource, then:

- Flag it: the backend's pages are keyset — `{items, nextCursor, hasNext}` requested with
  `first` / `after`.
- Title: "Keyset paging"

---

## Configuration, errors, Lombok

If configuration is not read through typed Spring field `@Value` placeholders on plain
configuration classes, then:

- Flag it. Keep configuration classes simple and classify every declared key.
- Title: "Configuration shape"

If a new config property has no `${TRELLIS_*:default}` key with a comment in the owning
`config/*.yml` file imported by `application.yml` (or its appropriate profile override),
or no classification in `SensitiveProperties`, then:

- Flag it: `ConfigClassificationTest` fails the build, and a secret-like key would otherwise be
  printable. Never disable that test.
- Title: "Classify new config property"
- Severity: high; security-high when the key is secret-like.

If an error is turned into an HTTP response inside a controller, or a second `@ControllerAdvice`
appears, then:

- Flag it: throw a `TrellisException` subclass carrying an `ErrorCode`; map a new framework
  exception by adding a `ProblemMapper` bean.
- Title: "Error handling bypasses ProblemMapper"

If `@Data` or `@Value` (Lombok) is used, or Lombok replaces what a `record` does, then:

- Flag it: `lombok.config` rejects both. Use the specific annotation needed; `val` is supported
  in tests and fixtures, and Spring test constructor injection may use `@RequiredArgsConstructor`.
  Spring field `@Value` on configuration classes is supported and is not Lombok `@Value`.
- Title: "Lombok overuse"

If `CompletableFuture.runAsync` / `supplyAsync` is called without an executor, or an executor is
created without `ContextPropagation.wrap`, then:

- Flag it: context and trace are lost. Inject `applicationTaskExecutor`.
- Title: "Async loses context"

If a javac warning is suppressed without a comment saying why, then:

- Flag it: the build runs `-Xlint:all -Werror`; warnings are fixed, not silenced.
- Title: "Suppressed warning without reason"

---

## Java naming and test structure

If a touched Java workflow retains ambiguous dependency/state names, deep setup nesting or
unrelated operations mixed into one method, then:

- Apply `docs/guide/java-testing-conventions.md` to the actual responsibility and control flow.
  Prefer private/local renames and cohesive helpers; preserve public contracts, SpEL parameters,
  exception handling, transaction boundaries and side-effect order.
- Title: "Workflow clarity and compatibility"
- Severity: medium for readability; blocking for an unintended behavior or security change.

If touched Java code uses wildcard imports, missing parameter `final`, unbraced control flow,
Java `var`, production Lombok `val`, or explicit immutable test locals instead of `val`, then:

- Apply AGENTS.md's production/test variable rules. Spotless owns wrapping and imports, but does
  not prove these structural rules; inspect any checks not enforced by the executed build.
- Title: "Java declaration conventions"
- Severity: medium.

If a test combines unrelated behaviors, depends on execution order, hides complex setup in an
assertion or uses an `IT` class suffix, then:

- Use focused `*Test` classes/methods and Given/When/Then sections. Extract named setup and
  transaction helpers; keep meaningful assertions visible and exception lambdas short.
- Title: "Focused independent behavior tests"
- Severity: medium.

If a test refactor removes persisted-state assertions, changes transaction/concurrency boundaries
or replaces the integration behavior under test with mocks, then:

- Restore the original observable outcomes and real collaborators. Compare scenarios/assertions,
  not just test counts or the presence of a successful test run.
- Title: "Preserve integration regression coverage"
- Severity: blocking.

If test SQL/probes duplicate an existing equivalent repository operation, then:

- Use that repository. Direct JDBC remains appropriate for a legacy migration/constraint,
  operator replay or a store without a matching API; explain the constraint locally.
  Do not invent production APIs just to eliminate justified migration SQL.
- Title: "Reuse persistence APIs in fixtures"
- Severity: medium.

---

## Contracts and migrations

If an endpoint, DTO or GraphQL schema changed without `contracts/openapi/openapi.json` /
`contracts/graphql/schema.graphqls` being re-exported and committed, then:

- Flag it: `checkApiContracts` runs in `check` and will fail. Then the clients need regenerating
  in `trellis-web` and `trellis-ai` (decision T22).
- Title: "Contract drift"
- Severity: high.

If an event payload changes shape inside an existing `contracts/events/<type>/v<N>.schema.json`,
then:

- Blocking. A breaking payload change is a new version, published alongside the old one during
  migration.
- Title: "Breaking event payload change"

If an already-applied Liquibase changeset is edited, then:

- Blocking: add `NNN+1-<change>.sql` instead. Schema changes are formatted SQL under the owning
  module's `src/main/resources/db/changelog/<module>/`, included at the **end** of the master
  changelog. Data backfill is a `MigrationStep` bean with checkpoints, not a changeset.
- Title: "Applied changeset edited"

If a change is breaking and `docs/how/breaking-changes.md` has no entry, or the commit lacks `!`
after the type, then:

- Flag it. The entry is written for someone who does not read code.
- Title: "Follow-through gap"

---

## GraphQL

If a GraphQL resolver authorizes, or holds logic, or does not take
`@ContextValue(GraphQlContextKeys.REQUEST_CONTEXT) RequestContext ctx`, then:

- Flag it: resolvers call the same application services as REST, so authorization lives in one
  place.
- Title: "Authorization only in a resolver"
- Severity: blocking.

If a field resolves per parent with `@SchemaMapping` where `@BatchMapping` would do, then:

- Flag it: that is an N+1 per request.
- Title: "N+1 in GraphQL"

---

## Confidentiality

If a real customer name, hostname, database or table name from a real system, or an internal
ticket id appears in code, tests, fixtures, comments or a commit message, then:

- Blocking. Use `example.test` domains and neutral sample data.
- Title: "Confidentiality"
