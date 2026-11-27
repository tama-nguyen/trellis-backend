# ADR 0005 — HTTP and operation rate limiting

Status — Accepted, 2026-11-24.

Native password authentication is available alongside Keycloak. The HTTP security chain resolves
both native bearer tokens and Keycloak JWTs. Abuse controls need stable distributed quotas before
expensive authentication and identity operations.

We apply two independent quotas. The security filter consumes an IP bucket for the seven POST
routes under /api/auth. Login/register share auth, forgot/reset share recovery, verify-email uses
action, and refresh/logout share session. CORS preflight and other routes proceed through their
existing security handling. The filter never reads the request body.

Public controller methods declare stable policy IDs with @RateLimited. The aspect consumes before
the controller delegates to the transactional use case. Identity policies canonicalize emails using EmailAddress.of before any lookup.
Notification and course create controller policies use the authenticated actor. Annotation thresholds move to configuration.

AuthController.forgotPassword declares @RateLimited with onExceeded=SKIP_VOID. The aspect consumes
before the transaction and completes an exhausted void call without invoking business logic.
The controller returns 202. Startup verification rejects SKIP_VOID on methods with a return value.
Store failures propagate as 503.
HTTP quota exhaustion returns 429 with Retry-After. Existing code/mail transaction semantics remain.

Production requires Redis and a dedicated shared HMAC secret and namespace. Keys contain stable
policy IDs and HMAC subjects. Multiple Bucket4j bandwidths consume atomically within one bucket.
IP and account consumes are independent and remain consumed if a later step fails. Redis errors
open a short circuit and return 503 on covered operations. GET /api/auth/me and management have
no dependency on the new HTTP limiter.

Forwarded client addresses are accepted only through explicitly configured trusted proxy CIDRs.
The resolver walks X-Forwarded-For from the peer toward the client, stopping at the first untrusted
hop. Deployment must strip incoming spoofed headers at its ingress and restrict origin access.
Automatic servlet forwarded-header rewriting must be disabled so the resolver sees the TCP peer.

Refresh rotation changes the token, so refresh/logout and verify use IP quota in this phase.
Adding session quota requires the verified session ID and a separate decision before rotation.

Rolling binary releases keep the same policy configuration, revision and key secret. Configuration
cutovers and rollback follow docs/guide/rate-limiting.md. A maintenance window may be as long as the
longest full-refill interval. Async email and production quota tuning require separate work.

The implementation uses the existing Bucket4j stores, Spring AOP and error mapping. Test coverage
includes private canonical keys, concurrent shared Redis quota, restart persistence, route scope,
trusted forwarding and recovery suppression.

Rate limiting is an HTTP boundary concern. Direct application calls execute their business logic
without HTTP throttling. Other adapters own their admission controls. Business authorization and
transactions stay on application services. Architecture rules enforce this placement.
