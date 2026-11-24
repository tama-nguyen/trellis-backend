# Security remediation operations

The implementation follows ADR 0006 and the remediation plan. JDK 21 build includes PostgreSQL
and Redis Testcontainers checks. Deployment validation and migration require the actual deployment
inventory and cannot be inferred from the development test database.

## Required deployment configuration

Set trellis.security.issuer-uri whenever JWKS/issuer JWT authentication is enabled. The public
issuer can differ from the internal JWKS HTTP address. Keep the production audience allowlist.
Native-only dev/test can omit both issuer and JWKS; JWTs are then rejected.

Provide TRELLIS_IDENTITY_MAIL_ENCRYPTION_KEY as a Base64-encoded, randomly generated 32-byte AES key.
The committed dev/test key is a development fixture. Store production key material in the deployment
secret manager and classify/redact it. trellis.identity.mail.key-version selects its version.
Deploy the same key/version to all mail workers. Worker defaults use a one-minute lease, five
attempts and a 30-second retry-delay multiplier after the lease. SMTP timeouts are configured
below the normal lease duration. Monitor trellis.identity.mail outcomes and due/exhausted jobs.

Test and setup/migrate modes disable delivery. When rotating the key, stop new issuance and drain
jobs under the current version before swapping the key/version on all replicas. A job encrypted
under an unavailable version is retried, then marked FAILED at the attempt bound. Keep the old key until
all jobs and rollback requirements are resolved. This implementation intentionally has one active
decryption version, so a rolling key change cannot safely process old jobs without the drain.

## Existing external principals

1. Inventory external role_grant subjects, course owners, notice authors/publishers, pending event
   envelopes and idempotency scopes before the identity key change. Native UUID identifiers remain.
2. Identify the trusted issuer and original subject/client ID for every external mapping. Compute
   the new ID through OidcPrincipalIdEncoder.userId/serviceId in platform/security/jwt.
   Empty or ambiguous issuer provenance must be resolved
   against the identity-provider/operator inventory. A native UUID collision requires row-level
   provenance review before assigning any existing grant or ownership.
3. Prepare a reviewed mapping and backup. Check uniqueness conflicts in role_grant. Merge only rows
   whose role/scope/expiry semantics have been explicitly reconciled. Preserve audit history.
4. Quiesce old writers and requests, drain pending events or transform their actor IDs under the
   same reviewed mapping. Apply external ownership/grant changes and deploy compatible replicas
   together. Existing external JWTs produce the new identity key without token content changes.
5. Validate native login, OIDC /me, ownership, scoped grants, list/detail and operator role management
   with real identities, then resume traffic. Replayed idempotency requests using the old principal
   namespace must be assessed before enabling retries.

No automatic issuer guessing or live identity-data backfill runs at application startup.
Rollback binary and identity mappings together while traffic is quiesced. Keep a reverse mapping
and reconcile new writes before rollback. Old binaries can otherwise restore identity collisions.

## Schema and transaction changes

Apply new Liquibase changesets through the deployment's schema workflow. The action-token
serialization changeset retains only the newest outstanding user/type action, ordered by created_at
and UUID. Inform support that older links/codes may become invalid. The unique index and user-row
locking protocol must be deployed together. Rolling back the index does not restore consumed actions.

identity_mail_job and security_event are new tables. Enqueue and business mutation commit together.
A send acknowledged by SMTP can repeat after a worker crash before completion. New issuance
invalidates the previous action; pending mail for invalid actions is retired on polling after any
active lease expires. External send still has a race with newer issuance or consumption.

Queue payload is encrypted at rest. SENT/CANCELLED jobs clear their payload. FAILED jobs retain
ciphertext while their action remains valid, allowing reviewed requeue after repairing delivery/key
configuration. When that action expires or is consumed, polling clears the failed payload too.
To requeue a specific valid FAILED job, transactionally set status=PENDING, attempts=0,
completed_at=NULL, lease_id=NULL, lease_until=NULL and next_attempt_at to current time, conditioned
on the action still being active and ciphertext being nonempty. Do not issue a new secret for a retry.
Worker cleanup deletes
completed rows older than one day. If workers remain disabled, schedule equivalent maintenance
with the same lease/invalidation conditions before deleting data. Keep action/token retention policy
separate from mail-job retention.

## Revocation, audit and monitoring

Role permissions and grants now read current primary-DB state; grants.cache-ttl/cache-max-size are
removed. Ensure the datasource routes authorization reads to the intended primary, and measure
query count, p95 latency and connection pressure. Revocation does not cancel work already authorized.
Token direct permissions and token-role mappings are separate authority sources and require their
own token/mapping lifecycle when revocation must remove those rights too.

audit_event records committed annotated mutations. security_event stores the fixed fields ID,
time, actor type/ID, action, outcome, stable error code and correlation ID. Anonymous login failures
carry no claimed email identity. SUCCESS outcomes occur after commit; REJECTED/ERROR outcomes survive
business rollback. Inspect trellis.security.audit{outcome=unavailable} and fallback logs for sink
failure. A success outcome can be absent after a crash between commit and record, while the
transactional mutation audit remains authoritative.

security_event has no public query endpoint. Restrict database reads to authorized operations/security
roles and use the organization's approved retention period. Purge by occurred_at in bounded batches
under that policy. Avoid exporting payloads, credentials or mail contents into log/analytics stores.

## Verification

Run targeted JUnit classes, then `./gradlew build` on JDK 21. The security scenarios are grouped into
`NativeAuthenticationTest`, `OidcIdentityIsolationTest`, `CourseAuthorizationTest`, `RoleGrantTest`,
`ActionTokenConcurrencyTest`, `AuthenticationAuditTest` and `IdentityMailDeliveryTest` under the app's
`identity`, `security` and `course` test packages. They retain real HTTP/PostgreSQL checks and mock
external SMTP delivery. See [the test refactor plan](../plan/plan-refactor-security-tests.md) for the
scenario mapping and verification commands. `JwtDecodersTest` uses signed JWTs and a local JWKS
endpoint. `OidcPrincipalIdEncoderTest`, `IdentityMailCipherTest` and `SecurityEventRecorderTest`
exercise isolated invariants.

Production rollout still needs the external identity mapping, issuer/JWKS topology, mail secret,
SMTP delivery checks and authorization-query load measurements. Keep the original evidence report
as a snapshot of its recorded commit, and consult the remediation status for working-tree changes.
