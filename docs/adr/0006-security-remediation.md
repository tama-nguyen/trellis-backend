# ADR 0006 — Identity isolation, committed authorization and durable security side effects

Accepted 2026-11-24 for the remediation implementation described in
`docs/tasks/TL-117/plan-security-remediation.md`.

## Decisions

The native UUID-ID clause is superseded by ADR 0004's generated Long identifiers (2026-11-27).
Native actor IDs are canonical positive decimal strings parsed by EntityIds; OIDC provenance must
still be checked before native lookup. JWT users and services receive distinct bounded principal keys
derived from a type prefix and SHA-256 over length-delimited validated issuer and subject/client ID.
User actors carry typed native/OIDC provenance. Native profile lookup requires native provenance.
OIDC ID encoding belongs to OidcPrincipalIdEncoder in platform/security/jwt. JwtActorMapper creates
the principal through generic Actor factories after encoding. Actor owns principal data and invariants.
An explicit JWKS location supplies signing keys and still requires a configured trusted issuer.
Role-name and stored-grant permission resolution accepts system roles only while tenant identity
and membership enforcement remain unimplemented.

The owner-approved grant follow-up checks native USER existence and registered resource existence
when creating a grant. External USER/SERVICE IDs must match their encoded namespace and need no
native row or IdP call. Global is exactly `(*, *)`; course scopes use canonical decimal Long IDs.
Application validation owns the rule; resource existence is resolved through registered adapters.
This does not add polymorphic foreign keys or retroactively invalidate stored opaque grants.

Course creation requires global course:manage. Update, activation and archive accept global or
exact-course MANAGE through the same loader. READ authorizes ACTIVE or own courses, including
scoped READ in both detail and paginated list. Ownership alone grants no mutation permission.

Permission resolution queries the configured primary database on each decision. No cross-request
permission cache is retained. A decision starting after a committed grant/mapping change reads
the current state. Already authorized requests may finish, and independent token rights still apply.
List grant expiry uses injected decision time, rather than an earlier HTTP request timestamp.

Action issuance and consumption lock the user before the action. A new partial unique index
allows one unconsumed action per user/type. The migration retires all but the latest outstanding
action deterministically. This can invalidate previously issued links/codes.

Native identity mail is enqueued atomically with business state. The identity mail queue stores
an AES-256-GCM encrypted payload, key version and hashed action reference. Claim/complete each have
short transactions. SMTP runs outside them, with bounded I/O timeouts. Lease recovery and retry
use the same action secret. Delivery is at least once, so duplicate or newly stale emails remain
possible around send/ack and newer issuance races. Setup/migrate and the test profile disable workers.

Successful native identity mutations append audit_event inside their business transaction.
SecurityAudited wraps permission/transaction advice and records attempt success after commit or
failure after rollback into security_event with an independent short transaction. Course/notification
access attempts record failures only. Bearer authentication failures use the same allowlisted recorder.
Sink failure increments a bounded metric and emits a structured fallback without exception details.
Neither table includes credential or request payload fields.

## Consequences

OIDC principal IDs, existing external ownership/grants and idempotency scopes need an explicit,
operator-reviewed migration. Issuer provenance cannot be recovered from an old subject string.
No live database was migrated as part of implementing this ADR. The runbook explains cutover.
Operator-only manifest scripts support explicit row selection, preflight, dry-run and reversal;
they reject unreviewed provenance and conflicting grants. They do not guess issuers or transform
dependent event/idempotency data automatically.

Authorization increases DB reads and requires query/latency monitoring. Removing a stored grant
does not revoke another authority source carried in a JWT. Mail needs a dedicated production key
and worker monitoring. SENT/CANCELLED job payload is cleared. FAILED ciphertext stays available for reviewed requeue
while the action is valid, and is cleared after action expiry/consumption. Terminal job
rows are cleaned after one day when the worker polls.

Security outcomes are attempt records. Transactional audit_event is the durable mutation record.
A crash after business commit and before outcome recording can leave a missing success outcome.
Audit sink outage does not block authentication solely to preserve attempt telemetry.

See docs/guide/security-remediation.md for migration, secrets, rollback and verification.
