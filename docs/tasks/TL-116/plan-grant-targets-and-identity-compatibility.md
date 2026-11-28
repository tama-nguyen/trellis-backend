# Grant targets and identity compatibility follow-up

Jira: [TL-116](https://tama-nguyen.atlassian.net/browse/TL-116)

Owner-approved contract, 2026-11-24: native grant subjects must exist; external USER/SERVICE
subjects use encoded principal IDs without requiring native user rows. Non-global scopes must
have a registered resource lookup and name an existing resource. Global is exactly `(*, *)`.
The current scoped authorization consumer is course, whose scope ID is the canonical decimal Long course ID (ADR 0004, updated 2026-11-27).

## Phase 1 — Validate grant targets

Add HTTP regression tests first. Validate inside `GrantRoleUseCase`, after its existing permission
and idempotency advice and before persisting. Keep native lookup through `UserRepository`; use an
application port for registered resource lookups, with a course adapter using the existing JPA
repository. Keep external ID format ownership in `OidcPrincipalIdEncoder`. Do not call an IdP or
require external identities to exist in `users`. Existence does not imply ACTIVE account status.

Reject malformed/type-mismatched subjects and unsupported/mixed-wildcard scopes with 400;
reject missing native users/resources with 404. Rejections must not create grants or successful
mutation audits. A denied caller still gets 403 before any target check. Existing idempotent
replays retain their stored response; they do not create a new grant or recheck target existence.

Existence validation observes state at grant creation; it does not introduce polymorphic foreign
keys or promise cleanup on future deletion. Do not change legacy stored grants during this slice.

## Phase 2 — Verify reviewed legacy identity mappings

Keep automatic issuer guessing disabled. Add a reproducible regression for an explicitly reviewed
mapping of legacy grants and ownership, including preserved native identities, uniqueness
conflicts, transaction rollback and bootstrap admin access. A migration test may use historical
data/SQL when the current repository API cannot express the legacy transformation. Exercise the
same operator artifact documented by the runbook, rather than inventing a migration only in tests.

No production mapping/data is available in this workspace. Do not execute a live backfill or
claim production cutover validation; unknown provenance and conflicting grants stop migration.
Pending events/idempotency scopes still require the coordinated drain/reconciliation in the runbook.

## Phase 3 — Documentation and verification

Clarify that registration queues encrypted mail transactionally; SMTP runs in the worker outside
the claim transaction and remains at least once. Update grant/migration contracts, breaking
changes and relevant conventions. Keep existing SMTP delivery tests unchanged except where
additional regression coverage is necessary.

Available checks (JDK 21, Docker): run `./gradlew spotlessApply` separately, then
`./gradlew :trellis-app:test --tests io.trellis.app.security.RoleGrantTargetValidationTest`,
the identity compatibility and existing security/identity suites, and `./gradlew build`.
Record executed commands/results below. Do not commit or push as part of this fix request.


## Executed verification

- Regression before the fix: `./gradlew :trellis-app:test --tests
  io.trellis.app.security.RoleGrantTargetValidationTest` failed in 22s: 16 cases ran, 9 invalid
  subject/resource/scope cases incorrectly received 201. The remaining cases passed.
- Initial validation fix: `./gradlew :trellis-app:test --tests
  io.trellis.app.security.RoleGrantTargetValidationTest --tests
  io.trellis.app.security.RoleGrantManagementTest`: BUILD SUCCESSFUL in 22s.
- Initial operator-script verification: `./gradlew :trellis-app:test --tests
  io.trellis.app.security.OidcPrincipalMigrationTest`: BUILD SUCCESSFUL in 19s, 8 cases.
- Final focused run: `./gradlew :trellis-app:test --tests
  io.trellis.app.security.RoleGrantTargetValidationTest --tests
  io.trellis.app.security.RoleGrantManagementTest --tests
  io.trellis.app.security.OidcPrincipalMigrationTest --tests
  io.trellis.app.OidcPrincipalIdEncoderTest`: BUILD SUCCESSFUL in 25s;
  50 tests, zero failures/errors/skipped (17 target validation, 5 existing grant management,
  11 migration and 17 encoder cases).

The migration cases execute the actual operator SQL against PostgreSQL: USER/SERVICE migration,
reviewed ownership/grants, native bootstrap admin preservation, missing issuer/unreviewed mappings,
forward/reverse grant conflicts, changed inventory, dry-run rollback, idempotent replay and reversal.
Fixture creation uses repositories; direct SQL is limited to operator manifest staging/execution.
Neither these tests nor SQL preflight establish the truth of external issuer provenance; the real
mapping must be computed with the canonical encoder and reviewed against the deployment inventory.
No production database, external IdP or live SMTP endpoint was accessed.

- Full verification: `./gradlew build`: BUILD SUCCESSFUL in 1m 4s; 213 tests,
  zero failures/errors/skipped (app 166, identity 43, common 2, platform-events 2), plus 1 passing
  API contract check. Spotless/compile/ArchUnit/context/config checks passed as part of build.
- `git diff --check`: exit 0. Java AST style audit passed for new validators, encoder and affected
  test/fixture classes (imports, final parameters, braces, var/val and short exception lambdas).
  Review skill metadata validation passed. Formatting was run separately before verification.

The operator artifacts and creation validator are implemented locally; no applied changeset,
configuration key, existing encoded-ID algorithm or generated API contract was changed.
Production rollout still needs a reviewed deployment-specific manifest, dependent-data
reconciliation, backup/dry run and coordinated bootstrap/access checks. No commit/push performed.


## Registry composition follow-up — 2026-11-25

Owner feedback: no stream/registry assembly in the `GrantTargetValidator` constructor.
`AuthorizationConfiguration` now constructs the scope-keyed immutable map and creates the validator
bean explicitly. `GrantTargetValidator` uses `@RequiredArgsConstructor` and has no component-scan
registration, avoiding a second bean or Spring's automatic bean-name-keyed `Map` injection.
The bean name remains `grantTargetValidator`. Duplicate scope keys still fail through the same
`toUnmodifiableMap` collector; validation code differs from the pre-refactor source only in blank-line grouping.
Rules/conventions record the assignment-only application-constructor pattern.

Executed checks (JDK 21):

- Before: `./gradlew :trellis-app:test --tests
  io.trellis.app.security.RoleGrantTargetValidationTest --tests
  io.trellis.app.security.RoleGrantManagementTest`: BUILD SUCCESSFUL in 21s.
- Formatter: `./gradlew spotlessApply`: BUILD SUCCESSFUL in 2s, separately from tests/build.
- After: same targeted command, BUILD SUCCESSFUL in 20s; 22 tests, zero failures/errors/skipped.
  Existing-course acceptance confirms registry keys remain authorization scope types.
- Source comparison: `requireValid` and private validation helpers retain identical non-blank code lines.
  Java AST style audit and `git diff --check` passed for the configuration/validator change.

No new test-only abstractions, validation policy changes, commits or production operations.

- Full wiring verification: `./gradlew build`, BUILD SUCCESSFUL in 1m 16s; 217 tests,
  zero failures/errors/skipped, plus 1 passing API contract check. Log: `/tmp/trellis-registry-build.log`.
- Owner's formatting feedback was then applied to the two changed classes: group dependencies,
  separate guards, lookup stages and return with meaningful blank lines. No non-blank validation
  code changed. `./gradlew spotlessApply` passed in 950ms, followed separately by
  `./gradlew spotlessCheck` and `git diff --check` (exit 0). No JUnit rerun for whitespace-only changes.
