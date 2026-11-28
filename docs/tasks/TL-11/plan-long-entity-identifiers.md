# Long entity identifiers and repository-based inserts

Jira: [TL-11](https://tama-nguyen.atlassian.net/browse/TL-11)

Status: implemented and committed on the feature branch, with verification recorded in
[the evidence/runbook](../../guide/long-entity-id-verification.md). The old local environment still
requires its owner-operated reset. No production infrastructure was changed.

Implementation branch: `feat/long-entity-identifiers` with merge baseline `0102f5e` (parents
`058a03c` and `eda055d`). Web prerequisite `fac1911` is present. All six phases below
have implementation/verification evidence; the owner's actual old-stack reset remains an operational
prerequisite. The isolated verification stack uses entirely fresh database and memory stores.
Inspected 2026-11-27. Local backend `main` is `37710e5`, three commits behind the locally available
`origin/main` ref `058a03c`. That ref includes the notification rename (`4916d72`) and generator
guide update (`d373e9f`). This inspection did not fetch remotes or change branches.

Required implementation baseline: integrate `origin/main` at least through `058a03c` and backend
`feat/authentication-user-security` through `eda055d`, preserving the owner's dirty persistence/mail
work. Web must include `feat/authentication-user-security` through `fac1911` (or descendants).
Record the resulting merge hashes and verify ancestor containment before phase 1. Do not implement
against local `37710e5` and rename later. Phase 0 is a prerequisite, not a suggestion.

## Goal and approved migration strategy

Replace UUID database row identifiers with Java `Long` / PostgreSQL `BIGINT` and use Spring Data
repository `save()` for JPA entity creation. Preserve business transitions, permissions, locks,
transaction boundaries, optimistic version checks, durable mail, and REST-first/selective GraphQL.

### Motivation and trade-offs

The expected benefit is a consistent repository creation API and smaller numeric entity keys.
The existing explicit `persist()` path already avoids Spring Data merge existence SELECTs; this
change does not remove an existing SELECT overhead. SEQUENCE with allocationSize 1 adds a `nextval`
call per generated ID compared with application-generated UUIDs. Measure actual SQL; do not claim
lower latency or better throughput without evidence. Sequence gaps and predictable numeric IDs are
accepted properties of the proposed design, not bugs or authorization mechanisms.

An internal service key can already select a delegated user; sequential IDs make enumeration much
easier (`1`, `2`, `3`). Document this trust boundary in the ADR: keep the key server-only, retain
user status checks and resource authorization, audit delegation, and never treat an unguessable ID
as access control. This plan does not silently add a new service allowlist or change delegation
policy. Confirm acceptance of enumerable native IDs at phase 0; if unacceptable, revisit the public
principal design before implementation rather than weakening authorization.

The owner confirmed that the project has no production database and will drop the old local
database. Edit the original Liquibase definitions and seed files directly. Do not add legacy-ID
columns, backfills, UUID-to-Long mapping tables, dual writes, or compatibility migrations.
Existing databases are intentionally incompatible with the new baseline and checksums.
The owner handles the local database reset; implementation does not authorize deleting volumes.
CI/test databases must also be recreated from the new baseline rather than bypassing checksums.

The reset unit also includes application-owned Redis state and Kafka events/retries/DLQs that may
carry the old actor IDs. Resetting PostgreSQL alone is insufficient; see the phase 0 checklist.

Do not use `clearCheckSums` to disguise an incompatible schema. Keep `ddl-auto=validate`.

## Verified current behavior and affected areas

Paths below are relative to the repository root; abbreviated Java packages resolve under their
module's `src/main/java/io/trellis/` directory.

| Area | Evidence and consequence |
| --- | --- |
| Native identity | `trellis-identity/.../infrastructure/persistence/{user,session,action,mail}`: five identity JPA entities use assigned UUIDs; `JpaUserRepository` uses `persist()` and explicit flushes. Including course and notification gives seven JPA entities in scope. |
| Registration | `identity/application/usecase/registration/RegisterUseCase.java` constructs a user UUID before insert, then uses that user for tokens, mail and audit result. Creation must return the persisted Long ID before these operations. |
| Shared primary key | `UserPasswordEntity.userId` is its primary key, assigned from the user. Switching its type alone will not make Spring Data `save()` identify it as new. |
| Business aggregates | `Course`, `Notification`, their entities, ports, mappers and inserts use UUID IDs. Immutable persisted aggregates require non-null IDs. |
| Authorization | `GrantTargetValidator` parses native USER IDs as UUID; grants and ownership store subject/resource identities as strings. Update parsing and producers together. OIDC encoded identities remain strings. |
| Pagination | `JpaCourseRepository` and `JpaNotificationRepository` sort by `(created_at, id)` with `UUID_KEY`. `KeyType.LONG` already exists. |
| Platform role storage | `platform/security/001-access-grant.sql` declares UUID role IDs referenced by role permissions and role grants. `JdbcRoleGrantStore` reads UUID role IDs. This is JDBC, not JPA. |
| Security outcome records | `platform/security/003-security-event.sql` has a UUID row PK; `SecurityEventRecorder` supplies it. Ordinary audit/outbox row IDs are already BIGINT. |
| Technical identifiers | `platform/events/001-outbox-and-inbox.sql` separates numeric outbox row ID from UUID event ID. Inbox deduplication uses event ID; do not change it. |
| Existing decision | ADR 0004 and `.agent-skills/review/standards/java.md` currently require `persist()` for assigned IDs. Update those rules with the implementation. |

The working tree contains owner edits in mail and identity persistence adapters. Re-read them at
implementation time and preserve their behavior. Names in the remaining plan refer to the required
post-merge baseline, not the stale local tree. Backend `eda055d` adds `InternalUserResolver`, whose
UUID native-user parsing must change in phase 1. Web contract generation and the native lifecycle
spec are provided by the feature branch and are available only after the stated baseline integration.

## Identifier contract

### Convert database row identities

| Table/entity | Target |
| --- | --- |
| `users` / `UserEntity` | Generated Long PK |
| `user_password` / `UserPasswordEntity` | Long shared PK `user_id`, assigned from persisted user |
| `user_session` / `UserSessionEntity` | Generated Long PK; Long `user_id` |
| `user_action_token` / `UserActionTokenEntity` | Generated Long PK; Long `user_id` |
| `identity_mail_job` / `IdentityMailJobEntity` | Generated Long PK; retain UUID `lease_id` |
| `course` / `CourseEntity` | Generated Long PK |
| `notification` / `NotificationEntity` | Generated Long PK |

The seven JPA entities are the complete implementation scope here. JDBC `role.id`, its references
and `security_event.id` retain UUIDs. Their independent refactor is described in
[the separate platform plan](plan-long-platform-row-identifiers.md) and is not a dependency of this plan.

Already numeric row keys remain numeric. Composite/natural keys are not replaced merely for uniformity.
Platform stores remain JDBC; this plan does not turn them into JPA repositories.

### Preserve identifiers with a different purpose

- UUID event IDs, inbox deduplication keys, tracing/correlation values, leases and lock ownership tokens.
- OIDC subject/issuer encoding and service principal namespaces; never parse these as Long.
- Opaque access/refresh/action secrets, token hashes, request idempotency keys and encryption keys.
- Public course/notification reference codes, role names, permission codes and scope type names.
- Polymorphic actor/subject/scope columns remain strings. Native user IDs and numeric resource IDs
  are serialized as canonical positive decimal strings after this refactor.

Do not remove `UuidV7` or `StableId` globally: first inspect their remaining technical consumers.
Do not rewrite immutable historical audit/event records. With the approved fresh baseline there
are no old persisted records to convert, but future event identity still needs its own contract.

## Persistence design

Use explicit per-table PostgreSQL sequences for generated JPA IDs and
`@GeneratedValue(strategy = SEQUENCE, generator = ...)` with a matching `@SequenceGenerator`.
Start with `allocationSize = 1` and matching sequence increment; tune only with evidence.
Column defaults must use the same sequence so native seeds and JPA cannot create overlapping IDs.
Sequence gaps on rollback are normal; IDs do not encode business order or commit order.

Keep sequence increment and Hibernate allocationSize at 1 for this design. Do not later enable a
pooled optimizer/allocationSize greater than 1 while native SQL still consumes this sequence via
column defaults. Such a change needs a separate allocator design, aligned increments and mixed
JPA/native concurrency tests proving that allocated ranges cannot overlap.

Dev seed strategy: omit generated user IDs and let the users sequence allocate them. In
`002-dev-users.sql`, resolve password `user_id` and grant `subject_id` by unique canonical seed email;
cast user ID to text for polymorphic subjects. Resolve existing UUID system role IDs by name and
system tenant predicate. Rollback statements resolve only these seed accounts, never all users.
Remove hardcoded UUID user/subject values. Tests must look up seeds by email rather than assume
IDs 1/2/3. With no explicit numeric seed IDs, no manual sequence RESTART is necessary. Test a fresh
seed followed by concurrent application inserts. Any future explicit seed ID requires documented
sequence synchronization before writers start.

New root entities reach `save()` with `id == null`. Use the returned managed entity when mapping the
persisted result. Persisted domain records keep non-null Long IDs. Do not introduce nullable IDs
into every domain transition or put JPA annotations in domain classes.

Creation ports accept new-state data without an ID and return the persisted aggregate/identifier.
Reuse existing creation details/commands where their semantics match; add a small creation value
only where needed. Define this per aggregate rather than creating a generic repository framework.

For the password shared PK, preserve the shared-key schema. A narrowly scoped `Persistable<Long>`
implementation with transient new-state tracking and `@PostPersist`/`@PostLoad` lifecycle callbacks
lets `passwords.save()` call `persist()` for new rows despite its assigned user ID. Test this path;
do not annotate `user_id` with `@GeneratedValue` or generate a second unrelated user ID.

Preserve intentional flush points: duplicate-email errors must surface inside the registration
error translation boundary, and the user must be present before dependent inserts/locks. Use
repository `flush()`/`saveAndFlush()` where needed; do not flush every operation indiscriminately.
Do not replace guarded version updates, revocation DML, row locks, keyset reads or lease completion
with unconditional `save()` calls. Removing direct EntityManager inserts does not forbid legitimate
EntityManager query use in existing list adapters.

## Access patterns and invariants

| Operation | Predicate/order | Invariant to preserve |
| --- | --- | --- |
| Register | Canonical email, unique email index | One account under concurrent duplicate registration; user/password/token/mail commit together |
| Native authentication | Token hash, expiry and account state; session-to-user join | Pending/disabled/expired/revoked credentials remain rejected |
| Refresh/reset/verification | Existing user/session/token lock order | Single-use actions and refresh rotation remain serialized |
| Course/notification lists | Existing visibility filters, `(created_at DESC, id DESC)`, bounded limit | Long tie-breaker; no authorization widening; no offset pagination |
| Scope lookup | Canonical resource ID, type and subject | Native/encoded OIDC identities remain distinct; no grant assigned to the wrong numeric namespace |
| Mail delivery | Due job, lease UUID and attempt state | Stale worker cannot complete another worker's lease; payload remains encrypted |
| Role lookup | Existing role name plus tenant predicate | Same role identity across permissions/grants; tenant isolation unchanged |

Production cardinalities, volumes and latency targets have not been measured. Do not claim an
index/performance benefit from this refactor without SQL counts or representative measurements.
Preserve uniqueness/check/index definitions, updating their column types as required. Do not add
foreign keys, cascade policies, relationship mappings or new indexes as incidental cleanup.

## Implementation phases

Each phase includes code and its regression tests. Deploy only the complete release against a
fresh schema; intermediate phases are review/build checkpoints, not rolling-upgrade releases.

### Phase 0 — Integrate prerequisites, freeze scope and define the reset

Inspect: `AGENTS.md`, ADRs 0002/0003/0004/0006, current owner edits, entity classes, all changelog
includes, generated contract inputs, seeds, fixtures and dev bootstrap scripts.

Deliver: integrate the required branch revisions described above with the owner's changes safely
preserved; resolve notification/auth conflicts and run baseline checks. Branch integration is planned
work, not an action performed by this document. Record exact merged revisions and generator versions.

Update ADR 0004 with motivation, measured/unmeasured benefits, sequence overhead, shared-PK exception
and fresh-database strategy. Explicitly supersede the UUID-native-ID and UUID-course-scope clauses
of ADR 0006 with the new Long/string contract; retain its OIDC isolation and security invariants.
Record an ID inventory and changed port signatures before coding.

Reset checklist for the owner, with exact local resource inventory captured before any action:

1. Stop all producers, API instances, relays, workers and replayers using this disposable environment.
2. Recreate the selected PostgreSQL database at the edited baseline; do not touch unrelated databases.
3. Clear only Trellis-owned Redis rate-limit/cache/session namespaces, or recreate its dedicated
   disposable Redis instance. Actor-based quota keys must not survive reassignment of ID `1`.
   Never use FLUSHALL on a shared instance. If namespaced erasure cannot be proven, isolate a new
   namespace/config revision and verify every relevant store uses it before restart.
4. Remove/recreate only owned disposable Kafka topics including retry/DLQ topics and associated
   consumer state, or provision a fresh isolated cluster/topic namespace for all producers/consumers.
   Resetting consumer offsets alone does not erase old events. Never replay archived events into
   the reset environment; `originator.id = "1"` may identify a different new account.
5. Clear local web session/query storage and owned persisted worker inbox/projection state that can
   reconnect to the new data. Reset cursor secrets or caches if desired, but still implement robust
   cursor-key validation in phase 3; secret rotation alone is not the correctness fix.
6. Start writers only after fresh schema, seeds and dependent stores are ready. Verify old tokens
   fail, old quotas do not affect new users and old Kafka deliveries cannot be consumed.

The reset is owner-operated and local-only. No generic volume, Redis or Kafka deletion command is
authorized by this plan. If any shared or production state is discovered, stop and redesign migration.

Acceptance: all numeric row keys and preserved technical UUIDs are classified; owner changes are
accounted for; database reset scope and contract revision are documented. No DB deletion is performed.
Stop if a shared/production database or a consumer requiring UUID compatibility is discovered.

### Phase 1 — Shared ID parser and the entire native-user caller chain

Change: `trellis-identity/.../domain/{user,session,action}`, `application/port/UserRepository`,
creation/authentication/profile/password use cases and support, `infrastructure/persistence/{user,session,action}`,
native security adapter; existing identity `001`, `002` dev seeds and `003` action-token SQL.
Also change `trellis-common/.../id/EntityIds.java` (new, framework-free canonical positive-Long parser),
`GrantTargetValidator`, `GetCurrentUserUseCase`, `NativeTokenAuthenticationProvider` and the merged
`InternalUserResolver`. Audit all `userById`, create-user, create-session and action-token callers,
including test/fixture signatures across `trellis-app`, in this same slice.
Update master includes only if a new file is actually needed; preserve changelog ordering.

Implement generated Long entities and returned saved user before password/token/mail work. Replace
UUID method parameters, row mappings, SQL bindings and user-ID parsing at all identity callers.
Keep passwords/tokens opaque and preserve locking/flush/exception boundaries.

Use the shared parser at all native user ID boundaries. Reject zero, negative, sign-prefixed,
leading-zero, whitespace, UUID and overflow input. Callers map parse failures to their existing
authentication/validation errors; the common parser does not depend on Spring or HTTP. Check OIDC
origin/encoding before native lookup. Authentication, grant validation and delegated-user lookup
must agree on canonical IDs. Course resource IDs remain UUID until phase 3; do not accidentally
switch their parser in phase 1. Native ownership/subject strings already become decimal here.

Compile all app tests after updating native-user fixtures, including native portions of
`PrincipalMigrationFixtures`/`OidcPrincipalMigrationTest`; their course/notification row IDs remain
UUID until phase 3. Port signature changes and all direct/indirect callers land atomically.

Tests: identity use-case suite; `IdentityRegistrationTest`, `NativeAuthenticationTest`,
`ActionTokenConcurrencyTest`, `ActionTokenMigrationTest`, `ForgotPasswordRateLimitTest`.
Also run `RoleGrantTargetValidationTest`, `OidcIdentityIsolationTest`, `OidcPrincipalMigrationTest`
and merged `InternalKeySecurityTest`. Add common parser boundary tests and delegated-native/OIDC
regressions. Use `:trellis-app:compileTestJava` as the first compile checkpoint before selected tests.
Migration tests use a historical fixture compatible with their actual purpose (action-token
serialization); they must not keep an accidental obsolete UUID assumption.

Acceptance: register -> verify -> login -> refresh -> reset -> logout persists correct Long references;
duplicate email rolls back cleanly; shared-PK password creation uses insert semantics; old password
and revoked sessions remain rejected; concurrent action redemption permits one success.
Stop on a returned null ID, duplicate-key translation regression or changed lock ordering.
Dependency: phase 0. Commands: identity tests plus selected app tests below.

### Phase 2 — Durable mail identifiers

Change: `IdentityMailJobEntity`, `IdentityMailJobRepository`, `JpaIdentityMailQueue`,
`IdentityMailClaim`, queue producers/workers and existing `004-identity-mail-job.sql`.
Reconcile the owner's current mail changes before modifying these files.

Use generated Long job IDs and repository save; retain UUID lease ownership, existing action hash,
encryption, claim transaction and completion predicates. Remove preassigned UUID job IDs only.

Tests: `IdentityMailDeliveryTest`, cipher tests and real PostgreSQL lease/rollback cases.
Acceptance: queued mail survives transaction/retry; aborted registration leaves no mail job;
concurrent claims cannot share an active job and stale leases cannot complete it.
Dependency: phase 1; stop on plaintext payloads, changed retry policy or SMTP moved into account transaction.

### Phase 3 — Course/notification IDs, resource grants, operator scripts and cursor errors

Change: `trellis-learning-content/.../{domain/course,application/course,infrastructure/persistence}`,
`trellis-notification/.../{domain,application,infrastructure/persistence}`, original course/notification
schema and seed files, and grant-resource lookups. Native-user validation was already changed in
phase 1; this slice changes only resource ID validation. Preserve public codes and route names from
the integrated baseline.

Return saved aggregates before constructing scopes, audit values or outbox messages. Use the shared
`EntityIds` parser for course/notification resource IDs. Keep phase-1 native actor parsing unchanged.

Keep the operator OIDC migration capability; it changes identity provenance, independently of this
fresh-database reset. Change `trellis-app/src/main/resources/db/operations/identity/prepare-principal-mapping.sql`
staging `course_id` and `notification_id` to BIGINT, then inspect sibling
`apply-principal-mapping.sql` (including its reverse mode), inventory queries and joins/casts.
Update `PrincipalMigrationFixtures` and `OidcPrincipalMigrationTest` resource
IDs in this slice. Preserve UUID `mapping_key`: it identifies an operator mapping, not an entity.
No obsolete notice table/column names remain at the integrated notification baseline.

Replace ID cursor key types with existing `KeyType.LONG`; retain signed opaque cursors, filters,
page-size bounds, deterministic timestamp/ID ordering and database visibility predicates. Old local
cursors are invalid after reset; return the established invalid-cursor error rather than a 500.

Explicitly change `trellis-platform/.../persistence/keyset/KeysetQuery.java` cursor arity and typed-key
conversion handling, with `KeyType`/`CursorCodec` changes only if needed. Codec signature validation
does not validate key types: a correctly signed UUID cursor can reach `Long::valueOf` after decode.
Translate invalid arity, numeric overflow, UUID-to-Long conversion and invalid timestamp keys into
the existing `INVALID_CURSOR` validation error before executing SQL. Do not globally map arbitrary
NumberFormatException to 400 or hide programming/SQL failures. Add a regression using the same
signing secret and a valid old UUID-key token: REST must return 400/INVALID_CURSOR, not 500.
Cover equivalent GraphQL error translation, valid Long cursors and other existing key types.

Tests: `CourseAuthorizationTest`, `NotificationFlowTest`, `RoleGrantTargetValidationTest`,
`RoleGrantManagementTest`, `OidcIdentityIsolationTest` and keyset cases in the existing test areas.
Include `OidcPrincipalMigrationTest` (selected-row provenance, reverse operation and rollback) and
the new focused keyset validation regression tests in this phase's targeted app test invocation.
Cover equal timestamps, multiple pages, concurrent insertion, hidden rows, owner/scoped grants,
global grants, invalid numeric IDs, stale revisions and OIDC/native separation.

Acceptance: list/detail access decisions agree, create/publish flows use saved IDs, stale updates
still fail, and a failed transaction does not append an outbox event. Dependency: phases 1–2.
Stop if identifiers escape through an unreviewed event/HTTP shape or authorization broadens.

### Phase 4 — Contracts, frontend integration and complete reset verification

Inspect/change: backend identity views/REST DTOs/event payloads, OpenAPI/GraphQL sources and existing
generators, `contracts/testdata`, bootstrap/dev fixtures, web auth adapters/types/tests and any scoped
ID UI. Both repositories must use the integrated notification contract before generation.

Newly Long entity identifiers exposed to JavaScript use decimal strings; `AuthenticatedUser.id`
already has this representation. Java Long can exceed JavaScript's safe integer range; no
`Number(id)` conversion or OpenAPI `uuid` format on new decimal IDs. Public aggregate URLs use codes.
Explicit compatibility exception: existing audit-event and role-grant `id` response fields remain
numeric OpenAPI `int64` in this refactor. Do not silently retype those APIs; their precision strategy
and any breaking string conversion require separate contract work. Do not claim these legacy numeric
fields are lossless above 2^53 in the current web client.
OIDC actor IDs remain encoded strings. Review every event field independently; retain existing
code/string fields where unchanged and version a breaking event shape instead of silently changing v1.

Regenerate contracts through their owning tools; never edit generated output. Recreate only
disposable test databases. Clear obsolete local frontend session state after the DB reset; old
credentials must fail authentication, not resolve to an accidentally reused numeric user.

Acceptance: fresh dev/test/prod-master schema installation validates, seeds load in order and all
Long sequences produce IDs above seeded maxima; API schema/client types agree; values above 2^53
round-trip without loss; browser native auth/course flows still work with mocks disabled.
Dependency: phases 0–3. The phase-0 cross-store reset is mandatory before integration acceptance;
database reset plus browser logout alone is insufficient. Stop on stale contracts, missing merged
feature branches, unresolved sibling rename, reusable external state or accidental token exposure.

### Phase 5 — Skills, rules, conventions, generator and handover

Update `AGENTS.md` (including canonical course scope ID), ADR 0004, explicitly superseded clauses in
ADR 0006, `docs/guide/trellis-common.md` (shared parser contract), Java/JUnit conventions,
`.agent-skills/review/standards/java.md`, database design/consistency/query skills where applicable,
`.agent-skills/java-refactor/SKILL.md`, `.agent-skills/new-module/SKILL.md` and module generator
templates only where their actual instructions conflict with generated Long IDs. Specifically edit
`build-logic/scripts/dev/new_module.py`: entity/SQL/port/creation templates, returned saved aggregate,
`UUID_KEY` cursor and `persist()` insert templates. The `.sh` wrapper is not the implementation.
Keep technical event UUID templates. Generate a disposable module outside the working tree using the
generator's actual `--help` interface; compile/run it in a disposable checkout and inspect generated
SQL/entity/cursor behavior before claiming new-module compliance.

Rules must cover: generated Long entity IDs; null-before-save root entity state; returned saved IDs;
shared-PK handling; decimal-string wire IDs; technical UUID exceptions; native/OIDC distinction;
Long cursor tie-breakers; repository save without replacing guarded updates; explicit SQL/transaction
semantics; and the approved pre-production reset exception to the usual immutable-migration rule.
Keep the default immutable-migration rule for databases already shared or deployed.

Retain Google Java Format, final parameters, explicit production local types, Lombok val only in
JUnit tests, descriptive names, Given/When/Then, no `IT` suffix, and real repositories for integration
fixtures. Do not introduce a blanket UUID ban, a JPA ban or a requirement to rewrite every native query.

Acceptance: no conflicting ID rule remains; a newly generated module follows the new conventions;
agent skill paths remain discoverable from AGENTS.md. Produce evidence and breaking-change notes.
Dependency: all prior phases; stop if documents claim tests that were not executed.

## Verification commands

### Compile and test checkpoints by phase

| Phase | Required checkpoint (run after its full atomic code/test slice) |
| --- | --- |
| 0 | Verify merge ancestry for the pinned backend/web prerequisites; baseline `:trellis-app:compileTestJava`, identity/app selected suites and web typecheck/contracts. Resolve baseline failures before ID changes. |
| 1 | `./gradlew :trellis-common:test :trellis-identity:test :trellis-app:compileTestJava`, then native registration/auth/action, internal-key, grant-target and OIDC tests listed above. Include parser tests. |
| 2 | `./gradlew :trellis-app:compileTestJava :trellis-identity:test`, then `IdentityMailDeliveryTest` and the new lease/rollback regression classes. |
| 3 | `./gradlew :trellis-app:compileTestJava`, then course/notification/grant/principal-migration tests and focused keyset regression classes. HTTP tests assert INVALID_CURSOR, not just parser exceptions. |
| 4 | Fresh-schema/reset verification, export/check contracts, full backend `build`, web gates and real native lifecycle/security-boundary Playwright checks. |
| 5 | Generator smoke verification in a disposable checkout, skill/reference consistency and final full `build`/contract checks after any generator/rule fixes. |

Compilation includes all test sources even when running one selected suite. A phase is not complete
if its new port signature leaves unrelated test fixtures uncompilable. Record exact selected test
counts and commands; no tests were run during this document-only revision.

These are existing repository commands, not results of this planning task. Use JDK 21 and Docker.
Run formatting separately, before compilation/tests. Select existing classes by the patterns below;
new meaningful regression classes must use the `*Test` convention.

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew spotlessApply
./gradlew :trellis-identity:test
./gradlew :trellis-app:compileTestJava
./gradlew :trellis-app:test --tests '*IdentityRegistrationTest' --tests '*NativeAuthenticationTest' --tests '*ActionTokenConcurrencyTest' --tests '*ActionTokenMigrationTest' --tests '*IdentityMailDeliveryTest'
./gradlew :trellis-app:test --tests '*InternalKeySecurityTest' --tests '*RoleGrantTargetValidationTest' --tests '*OidcIdentityIsolationTest' --tests '*OidcPrincipalMigrationTest'
./gradlew :trellis-app:test --tests '*CourseAuthorizationTest' --tests '*NotificationFlowTest' --tests '*RoleGrant*Test' --tests '*OidcPrincipalMigrationTest' --tests '*AuthenticationAuditTest' --tests '*SecurityEventRecorderTest'
./gradlew :trellis-app:exportApiContracts
./gradlew :trellis-app:checkApiContracts
./gradlew build
```

In trellis-web, after matching backend contract export:

```sh
npm run contracts:generate
npm run contracts:check
npm run typecheck
npm run lint
npm run format:check
npm test
npm run build
npm run test:e2e -- e2e/tests/auth/protected-routes.spec.ts
# With the real isolated backend/Mailpit environment, not just browser mocks:
E2E_BASE_URL=http://localhost:23100 E2E_MAILPIT_URL=http://localhost:28025 npm run test:e2e -- e2e/tests/auth/native-lifecycle.spec.ts e2e/tests/auth/security-boundary.spec.ts
```

Run the native lifecycle browser test with the isolated real backend/Mailpit environment described
in the existing authentication verification guide; a skipped test is not a successful verification.
Inspect actual SQL for new user/password/session/action/job/course/sample inserts to confirm insert
semantics and distinguish legitimate lookup/sequence SQL from a merge existence SELECT.
Do not assert exact total SQL counts that include unrelated audit, sequence or authentication work.

## Rollback and delivery

Keep phases as coherent commits only when commit is requested. No commit/push is authorized by this
planning task. No production source, dependency, schema, skill or rule is changed in this task.

Rollback is the prior code revision plus a fresh isolated environment (database, dependent
Redis/Kafka/worker state and browser credentials) at its matching old baseline.
It is not a supported downgrade against the new schema. Sequence reset, volume deletion and data
loss require explicit targeting/owner action. This is a pre-production reset design, not a future
production migration recipe.

Done means fresh schema + seeds + Hibernate validation, persisted Long references, working native
and OIDC authorization, durable mail/concurrency, stable pagination, precise client IDs, generated
contracts and consistent agent rules, with executed commands/results recorded per phase.
