# Generated Long entity IDs — verification and local reset

Implemented on `feat/long-entity-identifiers`. The backend baseline is merge commit `0102f5e`,
with parents `058a03c` (main with the notification rename) and `eda055d` (authentication and
internal-key delegation). Web verification used `feat/authentication-user-security` at `fac1911`
with regenerated contracts.

## Resulting contract

Users, passwords, sessions, action tokens, mail jobs, courses and notifications use Long/BIGINT row
IDs. Root entities use named sequences with allocationSize/increment 1; repository creation returns
saved domain objects. Password credentials keep the shared user PK and Persistable lifecycle state.
Native actor/resource IDs are canonical positive decimal strings. OIDC encoded IDs, technical event
and lease UUIDs, JDBC role/security-event IDs, public codes, guarded updates and locks are retained.
Existing audit/role-grant int64 wire IDs are an explicit exception to new decimal-string entity IDs.

The authoritative rules are ADR 0004, the superseded native-ID clauses in ADR 0006 and AGENTS.md.
The plan is `docs/tasks/TL-11/plan-long-entity-identifiers.md`; the optional JDBC work remains
separate.

## Initial implementation checks

All Gradle commands used JDK 21 and `--offline`. Formatters completed before compilation/testing.

The initial `./gradlew --offline build` exited 0 (`BUILD SUCCESSFUL in 1m 15s`). JUnit XML totals:
trellis-common 18, trellis-identity 44 and trellis-app 192: 254 tests, zero failures, errors or
skips. Results are under each module's `build/test-results/test/`. The separate generated-module
runtime smoke test passed in its disposable copy and is not included in that total.

| Command/check | Observed result |
| --- | --- |
| `./gradlew --offline :trellis-identity:test :trellis-app:compileTestJava` before ID edits | Passed against the integrated baseline |
| `./gradlew --offline spotlessApply` | Passed |
| `./gradlew --offline :trellis-common:test :trellis-identity:test :trellis-app:compileTestJava` | Passed after port/caller/fixture conversion |
| Targeted PostgreSQL integration run | Initial 109-test run had two failures: missing-native-user cases still supplied UUID strings. Updated those fixtures to missing canonical Long IDs; no production check was weakened. |
| `LongIdentityPersistenceTest` | Real user and shared-PK password inserts captured via Hibernate StatementInspector; no user/password merge existence SELECT; reloaded password has isNew=false |
| `CourseAuthorizationTest.shouldRejectSignedUuidCursorAfterSwitchingToLongIds` | Same-secret signed UUID cursor rejected through REST with 400/INVALID_CURSOR |
| `EntityIdsTest`, `KeysetCursorValidationTest` | Positive Long precision, noncanonical/overflow input, old UUID keys, timestamp and arity validation covered |
| `./gradlew --offline :trellis-app:exportApiContracts` | Passed |
| `./gradlew --offline build` | Passed, including ArchUnit, PostgreSQL/Redis tests and API drift checks |
| `node generate.mjs`, then `node generate.mjs --check` in `contracts/codegen/typescript` | Passed; TypeScript package sources/schema regenerated and drift check passed |
| `node ../trellis-web/node_modules/typescript/bin/tsc -p contracts/packages/npm/tsconfig.json` | Passed; npm package dist regenerated using the available TypeScript 6 compiler |
| `build-logic/scripts/contracts/generate.sh --python` | Passed; backend-owned Python contracts regenerated; AI repository unchanged |
| Web: `npm run contracts:generate`, `contracts:check`, `typecheck`, `lint`, `format:check`, `npm test` | Passed; 76 Vitest tests in nine files |
| `docker build -t trellis/web:long-id-check --build-arg NEXT_PUBLIC_MOCK_DOMAINS=false .` in web | Passed production build |
| `E2E_BASE_URL=http://localhost:23101 E2E_MAILPIT_URL=http://localhost:28026 npm run test:e2e -- e2e/tests/auth/native-lifecycle.spec.ts e2e/tests/auth/security-boundary.spec.ts` | 2 passed in 11.3 seconds against real Docker backend/PostgreSQL/Mailpit |
| `npm run test:e2e -- e2e/tests/auth/protected-routes.spec.ts --output="$TASK_PLAYWRIGHT_OUTPUT"` (isolated output directory) | 3 passed in 21.8 seconds against browser mocks |
| Skill creator `quick_validate.py` for database-design, database-consistency, database-query-optimization, java-refactor and new-module | All five valid; links remain discoverable from AGENTS.md |

The initial generator smoke used a disposable source copy:

```sh
build-logic/scripts/dev/new-module.sh --name catalogitem --aggregate CatalogItem --plural catalogitems --dry-run
build-logic/scripts/dev/new-module.sh --name catalogitem --aggregate CatalogItem --plural catalogitems --skip-contracts
./gradlew --offline :trellis-catalogitem:compileJava :trellis-app:compileJava
./gradlew --offline :trellis-app:test --tests '*CatalogItemPersistenceTest'
```

All passed after adding missing template imports. The smoke test booted the generated module against
fresh PostgreSQL, created a row with repository save and verified the generated Long ID and reloaded
aggregate. The generated module was confined to the disposable copy.

Two tooling retries are not production failures: package GraphQL generation must run from its
codegen directory for plugin resolution; a compiler executable absent in that directory was replaced
by the available pinned TypeScript 6 installation. The first concurrent Playwright runs shared the
default artifact directory and one failed at teardown with ENOENT; the isolated-output rerun passed.

## Review follow-up checks

| Command | Observed result |
| --- | --- |
| `./gradlew --offline spotlessApply` | Passed with JDK 21 |
| `./gradlew --offline build` | Passed, including API contract drift checks. JUnit XML recorded 256 tests across app, common, identity and platform events, with zero failures, errors or skips. |
| `./gradlew --offline :trellis-identity:spotlessCheck :trellis-identity:test` | Passed after removing the unused test clock. Identity ran 44 tests. |
| `git diff --check` | Passed |

The generator dry run listed the REST create policy and its YAML wiring without creating any files.
The generated module and HTTP smoke test ran in a disposable copy. The fixture granted
`catalogitem:manage` to the test administrator through Liquibase and set the create quota to
`1/PT1H` in that copy's YAML.

```sh
build-logic/scripts/dev/new-module.sh --name catalogitem --slug catalog-items --aggregate CatalogItem --plural catalogitems --features catalogitem,pricing --dry-run
build-logic/scripts/dev/new-module.sh --name catalogitem --slug catalog-items --aggregate CatalogItem --plural catalogitems --features catalogitem,pricing --skip-format --skip-contracts
./gradlew --offline :trellis-catalog-items:spotlessApply :trellis-app:spotlessApply
./gradlew --offline :trellis-catalog-items:spotlessCheck :trellis-app:test --tests '*CatalogItemRateLimitTest'
```

The first request returned `201` and persisted one draft with a generated Long ID and version 1.
The second request from the same actor returned `429` with `Retry-After`. The row count stayed at one.

### Sequence resolution

On a disposable PostgreSQL 17 database, `./gradlew --offline :trellis-app:bootRun
--args='--spring.profiles.active=dev,migrate --spring.liquibase.enabled=true'` completed successfully.
Queries against `pg_sequences` and `information_schema.columns` confirmed that all six sequences
specified by JPA exist, increment by one and are the defaults of their `BIGINT` ID columns.
`user_password.user_id` is a shared `BIGINT` key without a generated default. The IDE
[sequence troubleshooting guide](running.md#intellij-báo-cannot-resolve-sequence-course_id_seq)
explains how to distinguish the previous UUID schema from stale IDE metadata.

```sh
./gradlew --offline :trellis-app:test \
  --tests '*CourseAuthorizationTest' --tests '*NotificationFlowTest' \
  --tests '*LongIdentityPersistenceTest' --tests '*NativeAuthenticationTest' \
  --tests '*IdentityRegistrationTest'
```

The targeted run passed with 33 tests and zero failures, errors or skips.

## Docker data checks

The `trellis-long-id-check` project used a new named PostgreSQL volume, fresh Mailpit, in-memory
event transport and in-memory rate limits. It never connected to the existing Kafka/Redis state.
The backend was available at 28182/28183 and the web container at 23101. No live credentials were
printed in evidence. Checks observed:

- All three dev seeded accounts logged in; profile IDs were canonical decimal strings.
- Anonymous direct `GET /api/courses` returned 401.
- Information-schema inspection reported bigint for all seven converted PK columns.
- User sequence covered all inserted user IDs; password references and numeric native grants
  resolved to existing users.
- In the isolated database only, advanced the users sequence so registration generated
  `9007199254740993`. Real emailed verification, login and profile preserved exactly that string;
  internal-key delegation resolved the same native user without a bearer token.

This does not benchmark throughput or establish production rollout compatibility. The production
Liquibase master includes the same shared schema exercised by the tests; production secret/SMTP/TLS
infrastructure was not started. The old local application database and old external stores were
not reset. Application correctness was verified on fresh isolated stores.

## Owner reset before using the old local stack

1. Stop the API, producers, relays, workers and replayers for the intended disposable environment.
2. Identify its exact database/volume and recreate it at the edited Liquibase baseline. Never use
   clearCheckSums or reuse the UUID schema with this application.
3. Remove only owned Redis actor-quota/cache/session keys, or use a dedicated fresh namespace/instance.
   Never FLUSHALL a shared Redis server.
4. Recreate only owned Kafka topics/retries/DLQs and consumer/worker state, or use a fully isolated
   new topic/cluster namespace. Offsets alone do not remove old originator IDs; do not replay them
   into a new database where numeric identities may be reused.
5. Clear browser sessions, old cursors and dependent worker inbox/projection state before restarting.
6. Check schema validation, dev seeds and authentication before allowing new writes.

Rollback requires the prior application plus fresh matching database and dependent state. There is
no in-place downgrade/backfill. Shared/deployed environments require a separately reviewed migration.

Verification containers were stopped and removed after checking. The isolated PostgreSQL volume
was retained.
