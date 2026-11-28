---
name: database-design
description: Design or review Trellis PostgreSQL schemas, entity relationships, keys, constraints, lifecycle history, data ownership and safe migrations using access-pattern-driven design. Use when adding or changing persisted business data, ERDs, repositories or migration files.
---

# Database design

Use [ADR 0004](../../../docs/adr/0004-persistence-jpa.md) to place aggregate mappings and SQL
adapters. For polymorphic grants or external principals, verify the ownership contract before
proposing a foreign key.

For entity IDs, follow ADR 0004's Long/shared-PK/sequence rules and EntityIds parsing. Distinguish
entity keys from technical UUIDs. The owner-approved pre-production reset exception permits
baseline edits only for the selected disposable environment, never resetting shared data.
For each generated JPA ID, match the sequence name and allocation size to the Liquibase DDL
and verify the sequence and column default on a fresh PostgreSQL schema with Hibernate validation.
For an unresolved sequence in the IDE, inspect the connected schema before changing the mapping.

## Workflow

1. Read AGENTS.md and existing migrations, persistence mappings, repository methods, SRS/HLD/LLD and
   ADRs. Identify the checked-out revision and existing modifications. Find module owners and
   conventions. Distinguish implemented from planned behavior.

2. Identify actors, scopes, lifecycle and invariants before modeling tables. Write an access-pattern
   matrix: operation, actor, predicate, ordering, limit, typical/worst cardinality, estimated
   frequency, consistency requirement. Mark estimates as estimates.

3. Choose canonical entities, relationship cardinalities and ownership. Normalize source-of-truth
   data; prefer a join entity for many-to-many relationships with attributes (role, enrollment
   status, timestamps). Avoid copying mutable display data into every child unless a historical
   snapshot is explicitly required.

4. Specify primary/foreign keys, NOT NULL, UNIQUE and CHECK constraints. Use scoped uniqueness and
   composite foreign keys when they enforce the intended association. Define referential actions
   explicitly; avoid broad cascades for records that require retention. Validate soft-delete,
   rejoin, versioning and access revocation semantics. PostgreSQL CHECK constraints do not enforce
   cross-row cardinality.

5. Select indexes from the query matrix with the query-optimization skill. PostgreSQL does not
   automatically index referencing foreign-key columns; add appropriate supporting indexes when
   justified. Do not duplicate a unique/PK index.

6. Prepare a migration compatible with current data and deployments. Inspect existing migration
   style and tooling rather than introducing another tool. For large populated tables consider
   expand/backfill/validate/contract; estimate locks and disk impact. Concurrent index creation
   requires a non-transactional migration in PostgreSQL and a recovery plan for invalid indexes. No
   destructive migrations without explicit approval.

7. Verify with the existing test infrastructure against PostgreSQL: invalid references, uniqueness
   under concurrent inserts, nulls, soft deletion/history, resource-scope violations, migration on a
   realistic previous schema, application compatibility.

## Multi-agent compatibility

Follow the testing and access rules in [AGENTS.md](../../../AGENTS.md). Use repository files and existing PostgreSQL tooling. Record the evidence collected and any unmeasured assumptions.
