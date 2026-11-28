# 0004. JPA for business aggregates, JdbcClient for the rest

- Status: accepted
- Date: 2026-11-23
- Supersedes: the "no JPA" half of decision T10

## Context

T10 put every table behind native SQL on `JdbcClient`. It was never written down as an ADR; the only
record was a line in `ArchitectureTest.noJpa()`.

The cost showed up as the code grew. `JdbcAccountRepository` reached 317 lines for 18 methods, of which
roughly 110 lines are SQL and the rest is parameter binding repeated by hand plus a hand-written
`RowMapper`. Ten repositories and stores together are 1,287 lines. Simple lookups - find by id, find by
email, insert a row - cost as much to write and to read as the queries that are genuinely hard.

Nothing about those lookups is hard. What is hard in this codebase is narrow and well understood:
keyset pagination with a signed cursor, pushing a platform SQL fragment into another module's query,
and the platform's own batch stores.

## Decision

Business aggregates are persisted with Spring Data JPA. Everything below stays on `JdbcClient`:

| Stays on `JdbcClient` | Why |
| --- | --- |
| Keyset pagination (`KeysetQuery`, signed cursors) | Spring Data pages by offset, which skips and repeats rows when data is inserted between pages |
| `GrantPredicate` | It splices a platform SQL fragment into a business repository's query; JPQL cannot compose SQL |
| Outbox, inbox, idempotency, audit, role grants | Batch and lease-style access to tables that have no business aggregate |

Entities live in `<module>/infrastructure/persistence/entity`, never in `domain`: the domain keeps its
immutable records, and a mapper converts. `jakarta.persistence` and `org.hibernate` are allowed only
under `..infrastructure.persistence..`, which `ArchitectureTest.jpaStaysInPersistenceAdapters()` enforces.

Every statement is **native SQL**: a `@Query` on a repository is always `nativeQuery = true`, and JPQL is not
used. The point of moving to JPA was to delete binding code, not to gain a second query language - the text in
the file stays the text the database runs, so it can be pasted into psql and explained as written. Two
consequences follow and are not optional: enum parameters are bound as text, because a native statement skips
Hibernate's enum conversion; and `@Lock` does not apply to a native query, so a row lock is written as
`FOR UPDATE` in the statement itself.

A native `@Modifying` statement is invisible to the persistence context. Any that can run while an entity of
the same table is loaded carries `flushAutomatically` and `clearAutomatically`, or a pending insert runs after
it and an already-loaded entity keeps a stale value for the rest of the transaction.

### Generated entity identifiers (supersedes assigned UUID entity IDs, 2026-11-27)

The seven identity/course/notification JPA entities use Java `Long` / PostgreSQL `BIGINT` row IDs.
Root IDs are database-generated with a per-table sequence and explicit SEQUENCE generator.
Both allocationSize and sequence increment remain 1; native defaults use that same sequence.
Increasing allocationSize while native defaults consume the sequence requires a separately verified
mixed-writer allocator design. Sequence gaps are normal and do not encode commit order.

Creation ports accept new-state data without an ID and return the saved aggregate. Repository
save receives a null root ID; use the returned managed entity. Persisted domain records keep non-null
IDs and remain framework-free. The password's shared user PK uses Persistable lifecycle tracking
so repository save inserts its assigned key. Preserve required flushes and constraint error
translation. Do not replace guarded/native updates, row locks or lease checks with unconditional save.

The benefit is a uniform repository creation API and smaller row keys, not removal of an existing
merge SELECT: direct persist already avoided that lookup. AllocationSize 1 incurs a nextval call
per generated root ID; no throughput improvement is claimed. Numeric IDs are predictable. A trusted
internal service key can select users, and enumeration is easier with sequential IDs: keep it
server-only, retain account-state checks, audit and resource authorization, and never use ID secrecy
as access control. This does not change the approved delegation policy.

EntityIds in trellis-common parses canonical positive decimal native/resource IDs. Actor and scope
fields remain strings; OIDC encoded namespaces remain unchanged. Newly exposed Long entity IDs use
decimal strings for JavaScript precision. Existing audit/role-grant numeric int64 responses are an
explicit compatibility exception outside this refactor. Event IDs, lease/correlation/idempotency
UUIDs and JDBC role/security-event UUID row keys remain. Public codes remain unchanged. Long
keyset IDs retain timestamp/ID ordering; incompatible signed cursor keys return INVALID_CURSOR.

The owner approved editing original Liquibase definitions and dropping the old local database
because this project has not deployed. Seeds allocate user IDs normally and resolve references by
unique email. The exception requires a fresh isolated PostgreSQL/Redis/Kafka/worker environment;
old actor quotas and originators must not survive ID reuse. Do not reset shared infrastructure or
bypass checksum failures. Shared/deployed migrations remain immutable and require a separate
compatible migration design. No database deletion is performed by this implementation.

Entity conventions, all mandatory:

- Class named `<Aggregate>Entity`, always with an explicit `@Table(name = ...)`. A table whose name is a SQL
  reserved word takes the plural (`users`), never quoting.
- Always an explicit `@Column(name = ...)`; never rely on an implicit naming strategy, so a column name
  in Java and in the changeset can be compared by eye.
- `@Enumerated(EnumType.STRING)`, never ORDINAL.
- No `@OneToMany`/`@ManyToOne` between aggregates: they reference each other by id. The schema carries no
  foreign keys, and the two decisions agree on purpose.

Configuration, also mandatory:

| Property | Value | Why |
| --- | --- | --- |
| `spring.jpa.open-in-view` | `false` | Boot defaults it to true and warns. Leaving it on keeps a session open for the whole request, so lazy loading fires in the web layer - the thing `businessWebStaysThin` exists to prevent |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Liquibase owns the schema. Validation turns an entity that disagrees with its table into a startup failure instead of a runtime error |
| `hibernate.jdbc.batch_size` | `50` | Batches inserts and updates |
| `hibernate.order_inserts`, `order_updates` | `true` | Required for batching to take effect |

## Consequences

- Simple lookups become a method signature with the statement next to it; what disappears is the parameter
  binding and the `RowMapper`, not the SQL.
- Hibernate joins the runtime of every deployable that maps an aggregate, and `ddl-auto=validate` means an
  entity that drifts from its table stops the application from starting. That is the intent.
- Persistence is now written two ways. The boundary above is the rule; a query that could be either goes to
  JPA, and anything in the table above stays where it is.
- An entity plus a mapper is roughly 40 lines per aggregate that did not exist before. It replaces a larger
  amount of binding code, but it is not free.
- `package-info.java` is gone from the repository: it stated layer roles that ArchUnit already enforces, and a
  rule nobody can break silently is worth more than a comment nobody reads. A sub-package now exists once it
  holds a class, so the generator no longer creates empty ones.
- IDE warnings such as `Unable to resolve table 'account'` are unrelated to this decision: they come from
  IntelliJ having no data source attached, and native SQL inside `@Query(nativeQuery = true)` warns exactly
  the same way. `docs/guide/running.md` covers attaching one.
