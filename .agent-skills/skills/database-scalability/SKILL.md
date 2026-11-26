---
name: database-scalability
description: Evaluate Trellis PostgreSQL read models, denormalization, counters, hot rows, fan-out, retention/partitioning, Redis caching and Kafka projections. Use when measured access patterns reveal read/write bottlenecks or a derived-data design is required.
---

# Database scalability

PostgreSQL remains authoritative. Read [ADR 0005](../../../docs/adr/0005-layered-rate-limiting.md)
for mandatory production quotas and [ADR 0006](../../../docs/adr/0006-security-remediation.md) for
current authorization. For non-sequential completion, a maximum lesson number cannot represent which
lessons were completed.

## Workflow

1. Establish workload: rows/day, cardinality and skew, read/write QPS, access locality, latency
   budget, data retention, concurrency and growth. Use measured values where available; label
   assumptions. Inspect slow queries and indexes with database-query-optimization first.

2. Compare normalized query, better index/projection, synchronous denormalization and async
   projection. Quantify reads saved, additional writes, row-lock contention, storage, freshness and
   repair cost. Do not introduce caches or partitions because a table reaches an arbitrary row
   count.

3. If using a synchronous derived pointer or counter, update with authoritative state in the same
   transaction and use conditional/version guards. Define behavior for deletes, revocation, edits,
   reordering and backfill. If using async projections, define event contracts, inbox idempotency,
   out-of-order behavior, asOf/freshness, lag metrics and replay/rebuild. Derived data must not make
   authoritative grade or access decisions.

4. Estimate fan-out: one event may update many recipient rows. Compare compute-on-read, compute-on-
   write and hybrid using recipient size and access frequency. Kafka decouples time; it does not
   eliminate work. Avoid one hot aggregate row for extreme write concurrency unless ordering
   requirement justifies it.

5. Cache only after measurement. Distinguish the existing distributed quota store from proposed
   business caches. For proposed optional business cache, define keys, actor/tenant/version scope,
   TTL, invalidation after commit, stale refill races, capacity, serialization, stampede controls
   and degraded fallback. Never cache grants as authoritative or apply fail-open semantics to
   security quotas. Keep Redis readiness/outage policy separate from optional cache failure policy.

6. Consider partitioning only for evidenced query pruning, retention or maintenance needs. Compare
   time-range versus hash-by-resource; inspect query predicates, partition counts, foreign/unique
   constraints, migration cost and operational maintenance. In PostgreSQL partitioned-table
   unique/PK constraints must include partition key columns (subject to engine restrictions).
   Partitioning is not sharding and does not solve one hot resource.

7. Verify with representative load and failure injection: DB read/write latency, lock wait, index
   usage, outbox age, consumer lag, duplicate replay, projection consistency, Redis outage and
   rebuild correctness. Roll out in bounded phases with observability and rollback strategy.

## Multi-agent compatibility

Follow the testing and access rules in [AGENTS.md](../../../AGENTS.md). Use repository files and existing PostgreSQL tooling. Record the evidence collected and any unmeasured assumptions.
