# Java and JUnit conventions

This is the implementation reference for [AGENTS.md](../../AGENTS.md). Apply it to reviewed
changes incrementally; preserve business behavior, authorization, public contracts and persistence
semantics. Formatting alone does not establish that a workflow or test is readable.

## Production Java

- Use explicit imports, braces, and `final` method/constructor parameters. Use explicit local types,
  with `final` when the binding is not reassigned. Never use Java `var` or production Lombok `val`.
- Name dependencies by responsibility: `userRepository`, `passwordHasher`, `tokenCodec`,
  `authorizationService`. Distinguish raw tokens, hashes, decoded claims, entities and views when
  they coexist. A short name is fine when its meaning is unambiguous.
- Separate validation, preparation, state transition, persistence and result handling with logical
  blank lines. Extract cohesive operations; avoid helpers that merely rename a single obvious call.
- Keep one public `execute` per `<Verb><Noun>UseCase`, grouped by capability. Shared application
  collaborators live in `application/support`; controllers call use cases. `@RateLimited` stays
  on public controller methods, and quota services are not injected into business logic.
- Keep principal data/invariants in `Actor`; JWT-specific identity encoding belongs in
  `trellis-platform/security/jwt`. OIDC is the protocol, including Keycloak as one provider;
  use provider-specific names only for provider-specific behavior.
- Preserve parameter names used by SpEL, constructor/bean wiring, reflection and serialization.
  Preserve public DTO/record components, schemas, configuration keys, exception behavior,
  transaction boundaries and side-effect order during style changes.
- Keep plain data as records where appropriate. Configuration uses plain classes with typed
  Spring field `@Value`; this is distinct from the prohibited Lombok `@Value`. Keep defaults in
  configuration placeholders and avoid redundant constructor validation of those defaults.

## Persistence and package organization

JPA is supported. Business aggregates use Spring Data JPA adapters and entities in
`infrastructure/persistence`; repository `@Query` declarations use native SQL. Keyset pages,
grant predicates and platform stores use `JdbcClient` with named parameters. See the complete
persistence rules in [AGENTS.md](../../AGENTS.md#code-standards-java).

Group growing domain and persistence packages by actual feature/capability. Identity uses
`domain/user`, `session`, `action`, `token` and `infrastructure/persistence/user`, `session`,
`action`, `mail`. A `policy` package is appropriate for actual pure domain policies, not as a
mandatory folder for unrelated types. Preserve existing module boundaries and public packages.

## JUnit structure

- Name classes `*Test`, including PostgreSQL/HTTP/concurrency tests; do not append `IT`.
- Use descriptive behavior names. Avoid blanket `@DisplayName` annotations and generic names such
  as `useCase` when the specific subject (`loginUseCase`) makes setup clearer.
- Use Lombok `val` for immutable test and test-utility locals, and `final` method parameters.
  A variable that must be reassigned uses its explicit type. Never use `var`.
- Separate Arrange–Act–Assert with `// Given`, `// When`, `// Then`. Omit an empty Given section;
  `// When & Then` is appropriate for a direct exception assertion.
- Each test verifies one meaningful behavior and runs independently. Split unrelated scenarios,
  not assertions that together establish one outcome, such as status plus persisted state.
- Prefer standard JUnit assertions. Keep `assertThrows` lambdas short; invoke a descriptive
  helper when a failure requires transaction coordination or several operations.
- Keep actions and setup outside assertions. Avoid nested resource scopes, deep lambdas and
  long request/fixture chains inside test methods. Extract transaction execution, fixtures and
  repeated HTTP operations into helpers named for the operation they perform.
- Keep important assertions visible. Do not introduce a generic fixture framework, inheritance
  hierarchy or trivial helper for each statement.
- Group constants, dependencies, lifecycle state, tests and private helpers consistently.
  Spring integration tests use constructor injection: `@RequiredArgsConstructor` and
  `@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)`, directly or through an
  existing composed annotation. Mockito unit tests can initialize their subject in `@BeforeEach`.
  Avoid nested test classes used only to hide a second subject and its setup.

For example, the migration test keeps the action and failure assertion concise:

```java
@Test
void shouldRejectAnotherOutstandingActionAfterMigration() throws Exception {
  // Given
  val historicalActions = createHistoricalDuplicates();
  executeMigration(ACTION_SERIALIZATION);

  // When
  val failure =
      assertThrows(
          SQLException.class, () -> insertAnotherOutstandingAction(historicalActions.userId()));

  // Then
  assertEquals(UNIQUE_VIOLATION, failure.getSQLState());
  val unconsumedActions = unconsumedActionIds();
  assertEquals(List.of(historicalActions.latestActionId()), unconsumedActions);
}
```

Fixture creation, SQL execution and resource management remain private helpers in the
[actual test](../../trellis-app/src/test/java/io/trellis/app/ActionTokenMigrationTest.java).

## Fixtures and integration behavior

Use existing project repositories for fixture creation and persisted-state checks when their
operations match the scenario. Do not build a second SQL repository or probe for an API that
already exists. Limit direct JDBC to cases where a legacy schema, migration/constraint,
operator replay or write-only store has no equivalent repository API; explain that constraint.
Do not add a production read method solely to satisfy a test style preference.

Use real databases, transactions, HTTP, relay/inbox processing and concurrency coordination when
those are the behavior under test. Do not replace them with mocks during refactoring. Existing
unit mocks and controlled external-delivery substitutes can remain at their intended boundary.

Use JUnit/Testcontainers lifecycle hooks for isolated containers/connections. Shared application
integration tests use `PostgresTestcontainer`. Keep cleanup limited to owned fixtures. Preserve
commit/rollback boundaries, database assertions, ordering, permissions, expiry/retry boundaries
and concurrency outcomes. An unchanged test count does not prove unchanged assertion coverage;
compare the original scenarios and observable outcomes before/after extraction.

## Formatting and verification

Use the repository's pinned Google Java Format through Spotless. Match the IDE formatter to it;
accept its wrapping, import order and field-annotation layout. Do not add annotations or comments
to force line breaks.

1. Read the relevant implementation, callers, tests and configuration. Identify structural issues
   before editing; map existing scenarios and assertions to their refactored locations.
2. Run the existing focused test first when practical. Make incremental reviewed changes.
3. Run `./gradlew spotlessApply` to completion, separately from compilation/tests.
4. Run the affected test suites. For the migration example:
   `./gradlew :trellis-app:test --tests io.trellis.app.ActionTokenMigrationTest`.
5. Run `./gradlew build` for changes spanning modules, integration behavior or shared conventions
   implemented in source/build configuration. It includes Spotless, JUnit/ArchUnit and API contract
   checks; use JDK 21 and Docker for database/Redis integration tests.
6. Check `git diff --check`; inspect remaining naming, nesting and behavioral issues manually.
   Spotless does not enforce `val`, parameter modifiers, test isolation or meaningful assertions.

Record exact commands and observed results, including failures and skipped/not-run checks. Use
JUnit XML under each module's `build/test-results/` for counts. Do not infer that the whole build
passed from a targeted test or from a later successful formatting check.

## Commits and maintenance

Follow [Commits and PRs](../../AGENTS.md#commits-and-prs). When phased commits are requested,
include final production code and corresponding tests together, then synchronize plans, rollout
documentation, rules and skills. Avoid adding a commit for each review correction. Amend or
consolidate only the unpublished history authorized by the owner; preserve backups and user edits,
verify the final source tree and report the resulting commits. Never infer remote rewrite
authorization from a local cleanup request.
