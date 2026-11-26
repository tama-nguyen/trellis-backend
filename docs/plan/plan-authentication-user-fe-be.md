# Authentication and current-user integration

Status: native REST integration implemented and verified locally against Docker-backed services.
Production BFF session storage and browser OIDC login remain open decisions.

## Internal AI delegation boundary

The backend additionally accepts an internal, server-to-server delegation path for AI workers that
cannot present a user bearer token. It requires exactly one `X-Internal-Key` and one `X-User-Id`,
rejects a request that also supplies `Authorization`, and is disabled until `TRELLIS_INTERNAL_KEY`
is configured. The service key authenticates the calling service; the supplied user identity still
receives only its current database-backed permissions.

`X-User-Id` accepts an ACTIVE native user UUID or the existing encoded OIDC principal ID. Native
IDs are resolved through `UserRepository`; disabled, pending and unknown users are rejected. An
encoded OIDC ID remains external and does not require a row in native `users`; its effective grants
are resolved normally, with no JWT-only roles or permissions invented by the service key. This
matches the approved subject validation contract.

The Next.js BFF removes both headers from browser requests and upstream responses. Browser clients
continue to use bearer authentication only. The backend key is classified as sensitive and is never
emitted through actuator configuration endpoints. The audit identity remains the delegated user;
recording a distinct calling-service identity would require an approved audit-model extension and is
not silently inferred from a shared key.

Acceptance: an ACTIVE native user and an encoded OIDC principal can call authorized API endpoints
through the internal path; malformed, duplicate, mixed bearer/internal, disabled, pending, unknown
or incorrectly keyed requests receive 401 without disclosing the configured key. The BFF never
forwards either internal header. Existing Bearer JWT requests remain unchanged.

## Implementation checkpoint

Native forms now cover registration, verification, login, current user, refresh, logout and
password recovery using the emailed code. The site header uses AuthMenu and exposes account/logout.
Types are generated from the current backend contract. AuthTokens and AuthenticatedUser mark their
always-present fields as required in OpenAPI. Native credential rejection terminates provider lookup
so an unavailable Keycloak instance cannot convert a rejected native session into a 503.

Backend verification passed 44 identity tests and 14 selected app integration tests. The native
Playwright lifecycle passed with mocks disabled, real PostgreSQL and Mailpit. The production Docker
verification and exact commands are recorded in the integration evidence document.

These results cover native identity and current-user display. The browser currently retains its
existing token storage model. Production BFF session storage, CSRF, server-side route gating and OIDC
browser callback work require the decisions below. The email verification page uses an explicit
submit. Update the web CI schemaRevision after committing the backend contract changes.


## Goal and constraints

Connect the existing native identity lifecycle to localized web screens through REST and the
same-origin Next.js BFF. Native email/password is the primary flow. OIDC/Keycloak remains provider
two under ADR 0003. Preserve backend permission checks, quota aspects, audit, transaction boundaries,
encrypted durable mail jobs and absolute refresh-session expiry.

The downloaded SRS v0.3 describes Keycloak sign-in. Accepted ADR 0003 explicitly adds native identity
and makes Keycloak optional. Follow that accepted decision. This plan adds no administrative user
CRUD, profile editing, password-change endpoint, account linking, enrollment or new permission policy.
Only current-user retrieval exists in the inspected profile application area.

## Verified current implementation

Paths starting with `web/` refer to the sibling trellis-web repository.

| Evidence | Current behavior or gap |
| --- | --- |
| `trellis-identity/web/rest/AuthController.java` under the identity Java package | Eight REST endpoints listed below; delegates to application use cases. |
| `trellis-identity/.../application/view/AuthenticatedUser.java` | Current-user response has id, email, displayName and permissions. |
| `trellis-identity/.../application/usecase/profile/GetCurrentUserUseCase.java` | Native users come from UserRepository; OIDC profile comes from claims. Permissions resolve at global scope. |
| `trellis-identity/.../application/usecase/authentication/RefreshSessionUseCase.java` | Locks the active refresh session, rotates both tokens and preserves absolute expiry. |
| `trellis-identity/.../application/support/SignInGuard.java` | Pending verification and disabled users cannot sign in or refresh. |
| `web/src/entities/auth/auth.api.ts` | Login, refresh, logout and me adapters exist. Register, verification and recovery adapters are missing. |
| `web/src/entities/auth/auth.constants.ts` | Several descriptions still assume IdP-only authentication. Refresh triggers only on mock TOKEN_EXPIRED. |
| `web/src/entities/auth/auth.session.ts` | Access and refresh tokens are persisted in localStorage. |
| `web/src/entities/auth/auth.queries.ts` | Logout clears query cache. Session termination currently clears tokens without clearing all user data. |
| `web/src/features/auth/forms/LoginForm.tsx` | Login and safe redirect exist. Additional identity forms are absent. |
| `web/src/app/api/[...path]/route.ts` | General request proxy forwards headers and response bodies, including credentials. |
| `web/src/proxy.ts` | Locale routing only; protected access currently uses client guards. |
| `web/src/shared/api/generated/openapi.ts` | Current backend-generated identity schema is available for typed adapters. |

Resolve abbreviated Java paths through `trellis-identity/src/main/java/io/trellis/identity/`.

## REST contract and screen mapping

| Endpoint | Request | Success | Screen/result |
| --- | --- | --- | --- |
| POST /api/auth/register | email, password, displayName | 201, empty | Registration confirmation prompts the user to check email. |
| POST /api/auth/verify-email | token | 204 | Explicit verification action followed by login navigation. |
| POST /api/auth/login | email, password | 200, accessToken and refreshToken | Establish session, fetch me, navigate through safeRedirectPath. |
| POST /api/auth/forgot-password | email | 202, empty | Same confirmation for known and unknown accounts. |
| POST /api/auth/reset-password | email, code, newPassword | 204 | Password reset completion; previous sessions become invalid. |
| POST /api/auth/refresh | refreshToken | 200, rotated token pair | Refresh and replay the original request once. |
| POST /api/auth/logout | refreshToken | 204 | Revoke session and clear browser user state. |
| GET /api/auth/me | authenticated request | 200, AuthenticatedUser | Account panel and permission-aware navigation. |

Treat response codes and field errors through the existing Problem format and ApiError.
Map INVALID_CREDENTIALS, EMAIL_NOT_VERIFIED, USER_DISABLED, USER_ALREADY_EXISTS,
ACTION_TOKEN_INVALID, REFRESH_TOKEN_INVALID and quota responses from the actual exported contract.
Retain correlation IDs. A global permission list cannot represent all scoped course access.

## Decisions requiring review

1. Browser session storage. Recommend an opaque HttpOnly, Secure production cookie referencing
   server-side session data backed by PostgreSQL. This supports multi-instance refresh coordination
   and token confidentiality, but requires a reviewed BFF persistence boundary, retention and cleanup.
   An alternative is a reviewed encrypted HttpOnly credential cookie with key rotation and explicit
   handling for parallel refresh across requests/instances. It avoids a session table while making
   replay, concurrent rotation and response ordering harder. Do not select cookie cryptography or a
   new session dependency silently. Existing browser-held tokens can support local integration
   verification while this decision is pending. Production browser rollout depends on this decision.
2. Verification-link routing. Recommend the existing configured verification URI reach a web page
   that asks for an explicit submit before consuming the token, preventing automatic email scanners
   from activating it. Confirm locale fallback and preserve compatibility with outstanding links.
3. OIDC browser login. Resource-server JWT acceptance already exists; the inspected web app has no
   SSO callback. Decide whether this release includes Authorization Code + PKCE, state and nonce
   handling in the BFF. Account linking and email-based identity merging require separate approval.
4. User management. Confirm any requested profile editing or user administration against approved
   SRS/use cases before adding routes. Current-user display can proceed independently.

## Phase 1 — Align contracts and session errors

Files: web `src/entities/auth/auth.{api,types,constants,queries}.ts`,
`src/shared/api/http.ts`, `src/shared/api/generated/openapi.ts`, operation generator and backend
identity DTOs/error codes for comparison.

Use generated native DTO types; add typed registration, verification and recovery adapters.
Determine the actual native expired-token HTTP response before changing refresh classification.
Allow one refresh for an authenticated resource failure; exclude credential endpoints and preserve
single-flight behavior. Clear user-sensitive queries on session end and account switch. Handle
network outages independently from definitive credential rejection and avoid replaying writes
without the existing idempotency contract.

Acceptance: contract check validates adapter shapes; a real backend 401 refreshes once; failed
refresh terminates the session; concurrent requests share rotation in the selected session model;
account switching cannot display the preceding user's cached data. No repeated refresh loop.

Tests: Vitest for error classification, single-flight rotation, retry bounds and cache clearing.
Use real backend response fixtures with neutral values. Stop the session redesign portion if
storage/refresh concurrency decisions are unresolved.

## Phase 2 — Native login, current user and logout

Dependencies: phase 1 and the chosen session model for production acceptance.
Files: web login form, account panel, AuthMenu, RequireAuth, RequirePermission,
`src/proxy.ts`, API route handlers and auth query/session modules.

Use existing native login and me; connect real displayName, email and global permissions.
Preserve localized safe return paths. Distinguish pending verification, disabled user and invalid
credentials using backend codes. Implement authoritative session checks in the server boundary
selected in review; client guards supply navigation feedback. Verify CSRF protection, cookie
scope/lifetime, no-store responses and which headers the BFF forwards. Never log credential bodies.

Acceptance: authenticated account reload works; logout revokes the backend session; signed-out
protected navigation cannot disclose private content; no credentials in client-readable storage
under the approved production session model. Forged client permissions cannot authorize an API.

Tests: JUnit existing Login/Refresh/Logout/GetCurrentUser suites, Vitest adapters, Playwright with
mocks disabled for login, account reload, rejected redirect, logout and stale session recovery.

## Phase 3 — Registration and email verification

Files: web auth forms, localized site routes, route constants, auth mutations and en/vi/zh messages;
backend existing registration/verification use cases and IdentityRegistrationTest for verification.
Inspect IdentityProperties verification URI, encrypted mail worker and local Mailpit config.

Add register and verify-email screens. Preserve DTO limits and server validation. Registration
creates a pending account and durable encrypted mail job. Verification consumes the emailed token
through POST. Handle expired, invalid and reused tokens. Keep secrets out of analytics and logs;
remove the token from the visible URL after consumption. Verification never auto-submits on page load.

Acceptance: real emailed verification enables native login; pending users are refused; registration
never signs in implicitly; duplicate registration has no additional successful side effects.

Tests: extend existing registration JUnit scenarios only for verified integration gaps. Playwright
reads messages from isolated Mailpit and verifies single-use behavior. No paid mail service.
Stop if safe test-mail access or the approved redirect URI is unavailable; complete unit checks.

## Phase 4 — Forgot password and emailed reset code

Files: web forgot/reset forms and routes, auth adapters and localized errors; backend password use
cases, PasswordResetCodes and existing password/mail/quota suites.

Send email to forgot-password, then collect email, emailed code and new password. A password-confirm
field stays client-side. Preserve the generic 202 response, endpoint/aspect quotas, code expiry and
single use. Surface actionable expired-code errors without revealing whether an account exists.
Reset returns to login and invalidates all previous sessions as defined by the backend.

Acceptance: unknown and disabled addresses receive the same confirmation; the delivered code resets
an eligible account; wrong, expired and reused codes fail; old password and previous access/refresh
tokens fail after success; new password signs in. Concurrent redemption permits one success.

Tests: real PostgreSQL JUnit password/identity tests and controlled parallel redemption with separate
transactions. Browser test covers forgot -> mail -> reset -> login; assert persisted/session effects.

## Phase 5 — Optional OIDC and final verification

Implement the OIDC browser slice only after decision 3. Inspect existing issuer, audience and
principal encoding checks. Native and OIDC identities stay distinct, including identical emails.
An unavailable Keycloak instance must still allow native flows.

Run the combined native lifecycle against isolated PostgreSQL and Mailpit with auth mocks disabled.
Exercise 400/401/403/409/429, backend outage, email delay/retry, refresh races and cross-tab logout.
Verify permission revocation by calling the backend after revocation; UI cache is advisory.
Record commands, observed outcomes, side effects and unverified deployment behavior.

## Repository verification commands

Existing commands verified from package scripts and prior executed Gradle tasks. Use JDK 21 and
Docker for backend integration checks. Run formatting separately before tests.

```sh
# trellis-backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :trellis-identity:spotlessCheck :trellis-app:spotlessCheck
./gradlew :trellis-identity:test
./gradlew :trellis-app:test --tests '*IdentityRegistrationTest' --tests '*IdentityMailDeliveryTest' --tests '*ForgotPasswordRateLimitTest'
./gradlew :trellis-app:checkApiContracts
# trellis-web
npm run contracts:generate
npm run contracts:check
npm run typecheck
npm run lint
npm run format:check
npm run i18n:check
npm test
npm run build
# After the planned native test is added, use the real isolated frontend origin.
E2E_BASE_URL=http://localhost:23100 npm run test:e2e -- e2e/tests/auth/native-lifecycle.spec.ts
```

Internal delegation is covered by `InternalKeySecurityTest` and the web proxy boundary test. For a
disposable Docker stack, verify an ACTIVE native user directly against the backend with a configured
`TRELLIS_INTERNAL_KEY`; keep the value out of terminal history, source control and test output.

The browser spec above is a planned deliverable. Mock-disabled setup must start both applications,
configure the temporary frontend origin and verification URI, seed a disposable account or register
one, and clean up only owned services/data. Avoid using production infrastructure.

## Completion and delivery

Each implementation phase includes its code, meaningful regression tests and observed evidence.
Follow existing JUnit Given/When/Then, descriptive names, Lombok val in tests, final parameters and
Google Java Format. Keep business policies in existing application services and rate limiting at
its accepted HTTP/aspect boundary. Use real repositories for integration fixtures.

The integration is complete when all eight endpoint flows pass through the selected session boundary,
localized forms work with mocks disabled, email actions are verified against actual delivered mail,
refresh/revocation behavior is demonstrated, and unresolved release decisions are explicitly resolved.
This plan alone does not establish production browser readiness.
