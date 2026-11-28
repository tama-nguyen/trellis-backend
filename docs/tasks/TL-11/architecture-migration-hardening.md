# Architecture and migration hardening

Jira: [TL-11](https://tama-nguyen.atlassian.net/browse/TL-11)

Observed 2026-11-28. Baseline: latest `origin/main`, `8781af1be0908dda2fd979352ca01a3b2a27376e`.
Branch: `fix/TL-11-architecture-migration-safety`. Implementation authorized by the owner's
architecture-hardening request. The owner authorized local commits on 2026-11-28; push, database reset
and deployment are not authorized here.
TL-11 remains a completed foundation baseline, not a newly expanded product ticket.

## Finding plan and acceptance

Line references below refer to the baseline, before implementation. Severity reflects actual
behavior, not the breadth of a future feature. All SQL remains module-owned; app composes it.

| ID / severity | Evidence and root cause | Solution / affected files / dependencies | Risk, regression and acceptance |
| --- | --- | --- | --- |
| BE-01 high, confirmed | `CourseGrantResourceLookup.java:6,17` imports the course JPA adapter; `AuthorizationConfiguration.java:5` and `GrantTargetValidator.java:8` import identity's internal persistence port | Minimal `api` existence contracts implemented in owning applications through their existing persistence boundaries; app adapters consume only contracts | Preserve canonical Long parsing, external OIDC IDs, errors and global operator policy; real grant-target HTTP regressions and ArchUnit |
| BE-02 high, confirmed gap | `ArchitectureTest.java:210,328` covers platform/app and layers but not business/app or explicit cross-module public contracts | Add dependency-based rules, including app integration adapters, public API types and aggregate ownership; retain assembly/package scans | No blanket exclusions; actual compiled application must pass, deliberate violating fixtures must fail |
| BE-03 accepted design / future gap | ADR 0002 explicitly allows global grants; operator use cases select global authority | Preserve existing policy; delegated administration stays [TL-128](https://tama-nguyen.atlassian.net/browse/TL-128) | No new tenant, last-admin or delegation model; do not describe an ADR-approved operator as a vulnerability |
| BE-04 medium, confirmed | `build-logic/docker/env/backend.env:2`, `setup.env:5`: obsolete `local` profile | Use `dev`, `dev,setup`; inspect profile references precisely | Compose resolution and isolated one-shot startup; no existing volumes touched |
| BE-05 high, confirmed | `application.yml:14` defaults dev; prod transport defaults inmemory; no environment-combination guard | Early environment validation, exactly one dev/test/prod; explicit prod transport/config; incompatible operational flags rejected; disable consumers and scheduling in jobs | Preserve default dev convenience; bad prod configuration must fail before refresh; profile matrix + actual startup tests |
| BE-06 medium, confirmed | `SetupRunner.java:8,64,76,94` uses production `val` | Explicit local types, final parameters in touched production code | Formatting and compile; keep topic/S3 and exit behavior |
| MIG-01 accepted composition, immutability gap | Schema master contains all module definitions; dev wrapper alone includes `002-dev-users.sql` | Keep all existing changesets unchanged; freeze current pre-release baseline; future changes append | Byte/checksum/history verification; no checksum clearing, fake history, reset or MARK_RAN |
| MIG-02 high, test gap | Existing action-token test executes selected SQL, not full Liquibase history | Real Postgres/Liquibase fresh install, repeat update, genuine prefix upgrade with user data, checksum failure and Hibernate validation | No released supported database exists; prefix upgrade is tooling evidence only, not a claim of old UUID/notice database compatibility |
| MIG-03 medium, test gap | Environment wrappers appear correct but no migrated-data proof | Actual dev/test/prod changelog execution and seed/role assertions | Empty test/prod user tables; dev passwords/grants only in dev; common permissions in all |
| BE-07 high, confirmed | `MigrationRunner.java:116-137`: false means both busy and done; `RECORD_FAILURE` can overwrite DONE | Explicit batch result; bounded lock retry outside transactions; stable order/duplicate ID checks; validate progress; conditional failure under same lock; final DONE verification | Real independent-connection contention, timeout/interruption, rollback/resume and concurrent completion tests; job failure must return nonzero |
| MIG-04 high, confirmed | `MigrationAutoConfiguration.java:96-113` refuses on ApplicationReadyEvent, before Boot publishes ACCEPTING | Check blocking state before lifecycle start; startup failure prevents connectors, consumers and scheduled work; only start optional backfill after success | Actual SpringApplication lifecycle tests, failed/pending never reach readiness or start workers; completed steps serve normally |
| MIG-05 medium, operational design gap | One datasource currently used by all process modes | Document separate migration/runtime principals and minimum checkpoint DML permissions | No real DB role changes; runtime no DDL/Liquibase metadata writes; isolated least-privilege verification |
| DEP-01 accepted build-only CI / future gap | ADR 0007 and `.github/workflows/ci.yml` deliberately build without publishing | Document immutable promotion of one tested JAR/migration set, SHA/digest traceability | Workflow remains build-only; future release workflow proposed separately |
| DEP-02 high, confirmed lifecycle gap | prod currently permits automatic Liquibase; readiness guard too late; Kafka consumer lifecycle starts during refresh | prod serving and migrate do not apply schema; setup applies schema; operational isolation and pre-lifecycle migration verification | Setup → blocking migrations → verify → serving/Hibernate validate → readiness → smoke; actual contexts/isolated process checks |
| DEP-03 medium, documentation gap | Delivery docs lack compatibility/rollback/large-table lock protocol | Extend delivery/configuration/breaking-change guides, replace obsolete screenshot app description | Expand/backfill/old-writer drain/read switch/contract; distinguish app rollback from DB restore/forward fix; no destructive rollback automation |

## Owner decision: no operational profiles

Decided 2026-11-28. `application-setup.yml` and `application-migrate.yml` are removed; only `dev`, `test`
and `prod` remain. `trellis.setup.enabled` switches on the one-shot job, which now also runs the
migration steps. This supersedes the `setup`/`migrate` profile wording in BE-04, BE-05, MIG-04 and DEP-02
above; their intent (one environment, isolated one-shot job, schema applied only by setup in prod) stays.

## Owner decision: the app injects module ports

Decided 2026-11-28. This supersedes the BE-01 solution. The `UserExistence` and `CourseExistence`
contracts and their query classes are removed as pass-through layers. `trellis-app` injects
`UserRepository` and `CourseRepository` directly; the ArchUnit rule allows only the app to reach another
module's `application/port`. Persistence adapters, use cases and domain types stay closed to it.

## Owner decision: a smaller startup guard

Decided 2026-11-28 after review. This supersedes the guard part of BE-05. There is no environment post
processor: profile combinations are not checked at startup. Workers and the migration guard leave the
setup job through `@ConditionalOnServingApp`, production Liquibase follows `trellis.setup.enabled`, the
job declares `SPRING_MAIN_WEB_APPLICATION_TYPE=none`, and `SetupProperties` rejects switch values other
than true or false. Production keeps only the non-empty cursor secret check in
`PersistenceAutoConfiguration`; the HTTPS, development mail key, changelog and Liquibase override checks
are dropped. `SchedulingConfiguration` and `enforce-blocking` are removed.

## Owner decision: no data migration runner

Decided 2026-11-28. `MigrationRunner`, `MigrationStep`, the serving-mode migration guard, the
`trellis.migration.*` properties and the `migration_step_checkpoint` baseline changeset are removed: the
repository has no business data to backfill and no step was ever written. This supersedes BE-07 and
MIG-04, the checkpoint permissions in MIG-05, and the runner wording in the sections above. Removing the
changeset is an owner-approved edit of the unreleased baseline; later changesets stay append-only.

## Owner decision: changeset author

Decided 2026-11-28. Every formatted-SQL changeset is authored `tam.nguyenthanh`, including the module
generator template. This supersedes MIG-01's frozen baseline: Liquibase identifies a changeset by id,
author and path, so databases created earlier must be recreated. Later changesets stay append-only.

## Execution phases

1. Reproduce baseline build; fix module contracts, profile and setup code, then architecture tests.
2. Test real Liquibase execution, environment data, history, immutability and entity validation.
3. Harden runner with database concurrency regressions; move startup gate before lifecycle startup.
4. Document production accounts/release lifecycle and verify full build, contracts and isolated Compose.

No generated API/event contract change is intended. The Qodana files are committed separately on this
branch; the TL-124 plan belongs to another task and stays out of it.

## Jira boundaries and future work

- [TL-117](https://tama-nguyen.atlassian.net/browse/TL-117): security audit/resolution evidence;
  existing target validation is preserved, no additional security product scope claimed complete.
- [TL-125](https://tama-nguyen.atlassian.net/browse/TL-125): permission-aware admin navigation,
  still pending and unaffected by the infrastructure gate.
- [TL-128](https://tama-nguyen.atlassian.net/browse/TL-128): delegated grant authority,
  anti-escalation and last-admin behavior, still pending.
- [TL-131](https://tama-nguyen.atlassian.net/browse/TL-131): operator UI and recovery workflows,
  still pending.
- Proposed separate follow-up: **Release promotion and first supported database upgrade baseline**,
  owning registry/artifact retention, release attestations, restored production-sized upgrade trials,
  deployment credentials and rollout/restore drills. No new issue or scope change made implicitly.

## Verification evidence

Run 2026-11-28 with JDK 21.

| Check | Result |
| --- | --- |
| `./gradlew build` at `a759846` | passed, 261 tests, 0 failed |
| `./gradlew build` at `4e9e8a8`, `4bb13c0`, `6961f19` | passed, 265 tests, 0 failed at each |
| Setup job, then serving, on a throwaway Postgres and Redis (`dev` and `prod`) | passed |
| Four keyset lists, 2064 filter combinations, before and after the shared statement | identical results |
| Module generator output compiled in a copy of the repository | passed |
| Setup job on a database created with the former changeset author | fails as expected: the table already exists |

Not verified: `docker compose up`, image builds, the Qodana workflow on GitHub, Kafka transport and
topic creation, S3 bucket creation. Execution phase 3 (runner hardening) is superseded.
