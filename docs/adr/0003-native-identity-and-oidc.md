# 0003. Native identity with OIDC as a second provider

- Status: accepted
- Date: 2026-11-23
- Supersedes: ADR 0002 only where it assigned all authentication to an external identity provider

## Context

Trellis needs first-party Register, Login and Forgot Password flows using email and password. Keycloak/OIDC
must remain available as a second SSO provider, not the only credential store. The authorization decision in
ADR 0002 remains valid: regardless of how a user authenticates, Trellis permissions and scoped grants are
resolved inside Trellis.

Putting native credentials into Keycloak would make the requested first-party identity lifecycle an adapter
around Keycloak rather than a Trellis-owned feature. Putting identity tables in the platform would make a
generic infrastructure library own product data and password policy. Identity therefore has its own bounded
context and module.

## Decision

`trellis-identity` owns native accounts, adaptive password hashes, sessions and one-time email action tokens.
Password plaintext is accepted only at the command boundary and is never persisted or logged. Access,
refresh and email-verification secrets are generated from a cryptographic RNG. Password recovery uses a
human-readable, cryptographically random 60-bit code scoped to the canonical email address. PostgreSQL stores
only SHA-256 hashes of secrets and scoped reset codes.

Native access tokens are opaque and short-lived. A dedicated Spring Security authentication provider resolves
their hash against an active session and constructs the same typed `Actor` used for JWT authentication.
Refresh rotates both tokens while preserving the session's absolute expiry. Password reset revokes every
session for the account. Email verification and reset tokens are typed, single-use and expiring.

Keycloak stays enabled as provider two through Authorization Code + PKCE. Its JWTs continue through the
existing decoder and actor mapper. Native and OIDC subjects are never merged merely because their email claims
match; a future link operation must re-authenticate both identities.

Register, Login and Forgot Password are rate-limited below the controller. Login does not distinguish unknown
email from wrong password. Forgot Password always returns 202 for unknown, disabled, SMTP-failed and valid
accounts, so the wire response cannot enumerate users. Account-action mail goes through an application port;
SMTP is the production adapter and Mailpit provides the local end-to-end path.

## Consequences

- Trellis now stores security-sensitive identity data and must back it up, migrate it and apply retention rules.
- Keycloak is optional for native users but remains a supported SSO path; both paths share authorization.
- Native bearer authentication performs a database lookup per request, giving immediate revocation without a
  signing-key lifecycle. Caching can be added only if revocation lag is made explicit.
- The browser contract currently returns access and refresh tokens. Moving refresh tokens to an HTTP-only BFF
  cookie remains recommended before production browser deployment.
- Automatic native/OIDC account linking, MFA, recovery codes and personal access tokens remain deferred.
