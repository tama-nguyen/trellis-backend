---
name: database-consistency
description: Design and review Trellis transaction boundaries, concurrent writes, optimistic/pessimistic locking, idempotency, monotonic state updates, ordering, audit consistency and transactional outbox/inbox. Use for create/update/publish/enroll/submit/retry workflows and concurrency bugs.
---

# Database consistency

Trace command, transaction, outbox append, relay and inbox handler separately. Compare mutation
audit and attempt audit with [ADR 0006](../../../docs/adr/0006-security-remediation.md). For
concurrency evidence, identify the committed rows after both transactions finish.

## Workflow

1. Define required invariant, authoritative state, allowed transitions, transaction boundary, actor
   authorization and retry semantics. Identify possible races (lost update, duplicate insert, stale
   request, write skew, check-then-act, out-of-order commit and deadlock).

2. Inspect existing @Transactional, @Audited, @SecurityAudited, @RequiresPermission, @Idempotent and
   locking mechanisms before changing them. Verify actual proxy/advisor order and failure policy.
   Avoid self-invocation that bypasses proxies. Keep application logic transport-independent.

3. Prefer database constraints and atomic DML for simple invariants. Use version
   predicates/optimistic locking for edits, row locks for serialized transitions when justified, or
   an appropriate isolation level when multi-row invariants require it. Define consistent lock
   ordering and bounded retry for retriable serialization/deadlock failures. Do not use Redis-only
   locks as the correctness guarantee.

4. Idempotency: choose a stable operation key scoped to actor/resource/action, validate equivalent
   request payload, retain result and define expiration semantics. Enforce business uniqueness
   independently of optional HTTP keys. Reuse existing JDBC-backed idempotency store; inspect
   semantics before adding another mechanism.

5. Event delivery: write authoritative change and required outbox record in one database
   transaction, using existing platform APIs. Relay after commit, acknowledge broker result before
   marking delivered; expect duplicates. Consumer applies dedupe and local DB effects atomically,
   acknowledges after durable commit; use event/version guards for ordering. External effects
   require separate idempotency or durable delivery semantics. Never claim exactly-once end-to-end
   from Kafka alone.

6. Test races using real PostgreSQL, independent transactions/connections and deterministic barriers
   rather than sleeps. Assert final invariants, not just status codes. Cover retry after commit/lost
   response, rollback, stale revision, duplicate event and replay. Check lock timeouts and failure
   mapping.

## Multi-agent compatibility

Follow the testing and access rules in [AGENTS.md](../../../AGENTS.md). Use repository files and existing PostgreSQL tooling. Record the evidence collected and any unmeasured assumptions.
