---
name: database-query-optimization
description: Analyze and improve Trellis PostgreSQL SQL, repository queries, B-tree composite or partial indexes, cursor pagination, joins, execution plans and N+1 issues. Use for slow queries, list endpoints, data access and database performance reviews.
---

# Database query optimization

Trace REST and GraphQL through the shared query use case. Inspect the actual SQL and bound scope in
the persistence adapter. Reuse KeysetQuery and the existing signed cursor contract. See [ADR
0004](../../../docs/adr/0004-persistence-jpa.md).

## Workflow

1. Locate the real SQL (including ORM-generated SQL), bound parameters, existing indexes,
   constraints and row distribution. Inspect repository ownership, authorization predicates and
   paging contract. State baseline assumptions if representative data is unavailable.

2. Identify equality filters, range conditions, sort order, tie-breaker and limit. Evaluate B-tree
   index candidates with leading equality columns and appropriate range/order columns. Consider
   selectivity and other workload queries; column order is not a universal formula.

3. Run EXPLAIN (ANALYZE, BUFFERS) for representative read queries on a safe database, compare
   estimated/actual rows, loops, index conditions, filters, sort, heap fetches, buffer hits/reads
   and runtime. ANALYZE executes the statement; never run against mutating production SQL without
   approved safe procedures. Compare common and skewed parameters; record dataset size and
   conditions.

4. Use explicit projections instead of unbounded SELECT * when practical. Avoid fetching collections
   in loops; prefer bulk queries or justified ORM fetch strategies. GraphQL @BatchMapping/DataLoader
   is for actual nested fan-out, not every GET; measure SQL count and total work. Do not bypass
   authorization via bulk queries.

5. For deep lists, prefer deterministic keyset pagination. Include a stable unique tie-breaker and
   scope/filter-compatible cursor; validate cursor format, enforce maximum page size, and apply the
   same visibility predicate on every page. Do not claim snapshot consistency if rows can change or
   commit late.

6. Check index overhead: duplicated indexes, write amplification, page size, bloat and maintenance.
   Partial indexes are useful only if query predicate implies index predicate; prepared generic
   plans may affect use. Covering indexes can reduce heap access only when visibility and selected
   columns permit. PostgreSQL B-tree supports backward scans; do not add ASC/DESC duplicates
   blindly.

7. Within the project testing policy, verify SQL count, correctness, visibility and pagination.
   Benchmark before/after under representative data; document remaining limits.

## Multi-agent compatibility

Follow the testing and access rules in [AGENTS.md](../../../AGENTS.md). Use repository files and existing PostgreSQL tooling. Record the evidence collected and any unmeasured assumptions.
