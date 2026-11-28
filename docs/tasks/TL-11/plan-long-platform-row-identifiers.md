# Platform JDBC row identifiers — separate proposal

Jira: [TL-11](https://tama-nguyen.atlassian.net/browse/TL-11)

Status: deferred proposal; not approved for implementation and not required by
[the JPA Long entity plan](plan-long-entity-identifiers.md).

## Scope and rationale

Evaluate changing `role.id` and `security_event.id` from UUID to generated BIGINT. Platform stores
remain JDBC. This change does not enable Spring Data `save()` and has no demonstrated performance
requirement; evaluate whether the extra consistency benefit warrants the scope before proceeding.
Existing role-grant, audit and outbox numeric row IDs already need no type conversion.
Event IDs, correlation values, leases, encoded external principals and mapping-operation UUIDs stay.

Baseline must contain backend `origin/main` through `058a03c` and the approved JPA work if it has
landed. Resolve names/revisions at implementation time; do not implement against the stale local
notification predecessor. Preserve all owner work. No code or database changes occur in this plan.

## Proposed slices, subject to separate approval

1. Inventory every role PK reference, seed, lookup/model, API and fixture. Inspect
   `platform/security/001-access-grant.sql`, `002-platform-roles.sql`,
   `003-security-event.sql`, `JdbcRoleGrantStore` and `SecurityEventRecorder`. Record whether
   any role UUID escapes into consumer contracts. Stop on unknown external compatibility needs.
2. If still pre-production with explicit fresh-environment approval, edit original schema/seed
   definitions and JDBC inserts. Role permission/grant reference columns must match the role PK.
   Resolve generated role IDs by tenant-aware name; never independently assign seed references.
   Use generated row identity for security records without changing audit/event correlation data.
   If the environment is shared/deployed, require a separate non-destructive migration design.
3. Verify tenant/system-role lookup, grants/revocation, security outcome persistence and failed
   transaction behavior. Include `RoleGrantTest`, `RoleGrantManagementTest`,
   `RoleGrantTargetValidationTest`, `OidcPrincipalMigrationTest`, `AuthenticationAuditTest` and
   `SecurityEventRecorderTest`; assert database outcomes using existing repositories/fixtures.
4. Review wire contracts independently. Existing role-grant/audit IDs are numeric int64: retaining
   them is an explicit compatibility exception, not a claim of JavaScript precision above 2^53.
   Any decimal-string change needs a versioned breaking contract decision and client tests.
5. Update the relevant ADRs/rules and verification evidence only after the scope is approved.

## Commands and acceptance

Use JDK 21, Docker, formatting before tests, and existing tasks:

```sh
./gradlew spotlessApply
./gradlew :trellis-app:compileTestJava
./gradlew :trellis-app:test --tests '*RoleGrant*Test' --tests '*OidcPrincipalMigrationTest' --tests '*AuthenticationAuditTest' --tests '*SecurityEventRecorderTest'
./gradlew :trellis-app:checkApiContracts
./gradlew build
```

No commands were executed by this planning task. Acceptance requires consistent role references,
unchanged tenant isolation/permissions, recorded security outcomes and explicit contract compatibility.
The JPA entity plan can finish while these JDBC UUID row keys remain unchanged.

Any approved reset/rollback must use an explicitly inventoried disposable database, Redis/Kafka
and dependent worker environment; row/principal ID reuse must not attach old state to new actors.
Do not delete shared resources, commit, push or perform this proposal without a separate request.
