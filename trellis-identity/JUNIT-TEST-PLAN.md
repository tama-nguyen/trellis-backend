# JUnit test plan — `trellis-identity` use cases

Status: approved by the module owner on 2026-11-24. This is the explicit T5 exception for the native identity
use cases.

## Scope

Write fast unit tests for every application use case and for the three application helpers that carry shared
security behavior. Persistence query integration, REST validation, Spring AOP wiring, and SMTP delivery are
separate test slices and are not part of this plan.

Production and test classes map one-to-one:

```text
LoginUseCase                 -> LoginUseCaseTest
LogoutUseCase                -> LogoutUseCaseTest
RefreshSessionUseCase        -> RefreshSessionUseCaseTest
RegisterUseCase              -> RegisterUseCaseTest
VerifyEmailUseCase           -> VerifyEmailUseCaseTest
ForgotPasswordUseCase        -> ForgotPasswordUseCaseTest
ResetPasswordUseCase         -> ResetPasswordUseCaseTest
GetCurrentUserUseCase        -> GetCurrentUserUseCaseTest
ActionTokens                 -> ActionTokensTest
PasswordResetCodes           -> PasswordResetCodesTest
SignInGuard                  -> SignInGuardTest
```

Do not group independent use cases in one class and do not use `@Nested` here. JUnit documents `@Nested` as
a way to express hierarchical state and shared lifecycle within one subject. These use cases are independent
subjects with different collaborators and fixtures.

## Test conventions

- Use the versions already resolved by the build: JUnit Jupiter 6.0.3 and Mockito 5.20.0.
- Use package-private test classes and methods, one top-level test class per production class.
- Use `@ExtendWith(MockitoExtension.class)` and `@Mock` for ports owned by this codebase. Construct the system
  under test explicitly in `@BeforeEach`; do not hide constructor behavior behind `@InjectMocks`.
- Use real `User`, `UserSession`, `UserActionToken`, `EmailAddress`, `IdentityProperties`, `RequestContext`,
  `Instant`, and `Clock` values. Never mock records, value objects, or third-party framework types.
- Name tests by observable behavior, for example `rejectsUnknownEmailAfterDummyHashCheck`.
- Assert returned values, stable `IdentityErrorCode` values, persisted arguments, and security-relevant absence
  of side effects. Do not assert exception prose, annotations, private methods, or framework implementation.
- Capture arguments only when the use case creates a value that cannot be supplied by the test, such as a
  UUIDv7 or the normalized `User` passed to the repository.
- Use `never()` or `verifyNoInteractions()` only where “must not perform this side effect” is part of the
  security behavior. Do not add blanket `verifyNoMoreInteractions()` checks.
- Keep each test Arrange–Act–Assert, with no Spring context. Stubbing belongs in the test that needs it; shared
  setup only constructs stable fixtures and the system under test.

These choices follow the [JUnit User Guide](https://docs.junit.org/6.0.3/writing-tests/intro.html), its
[guidance for nested tests](https://docs.junit.org/6.0.3/writing-tests/nested-tests.html), and Mockito's
[official good-test guidance](https://github.com/mockito/mockito/wiki/How-to-write-good-tests).

## Behavior matrix

### Authentication

- `LoginUseCase`
  - canonicalizes email, verifies the stored hash, issues access/refresh secrets, and persists only hashes;
  - unknown email still calls the password matcher with the constructor-created dummy hash;
  - wrong password returns `INVALID_CREDENTIALS` and creates no session;
  - pending and disabled users are rejected before token issuance.
- `RefreshSessionUseCase`
  - rejects a non-refresh token before repository lookup;
  - rejects an unknown, expired, revoked, or rotated token through the same `REFRESH_TOKEN_INVALID` result;
  - rotates both secrets for an active user while preserving the session's absolute refresh expiry;
  - pending and disabled users cannot refresh.
- `LogoutUseCase`
  - hashes and revokes a refresh token at `RequestContext.requestTime`;
  - a token from another family is an idempotent no-op.

### Registration and actions

- `RegisterUseCase`
  - normalizes email and display name, hashes the password, creates a pending user, issues verification, and
    sends only the raw action token;
  - maps the database uniqueness race to `USER_ALREADY_EXISTS` and performs no token/mail side effect.
- `VerifyEmailUseCase`
  - locks a verification action, verifies its user, then consumes that exact action at request time;
  - propagates invalid action-token failure without mutating the user.
- `ActionTokens`
  - issues an ACTION secret, stores only its hash with the configured absolute expiry, and returns the raw
    value to the caller;
  - rejects the wrong token family before hashing or repository access;
  - maps a missing/expired/consumed action to `ACTION_TOKEN_INVALID`.
- `PasswordResetCodes`
  - issues a human-readable reset code and stores only its email-scoped hash;
  - canonicalizes both email and code before lookup;
  - rejects malformed, missing, expired, or consumed codes as `ACTION_TOKEN_INVALID`.

### Password recovery

- `ForgotPasswordUseCase`
  - does nothing observably for an unknown address or disabled user;
  - issues and mails a reset code for an eligible user;
  - suppresses `MailException` so delivery failure cannot become a user-enumeration oracle.
- `ResetPasswordUseCase`
  - locks an email-scoped reset code, hashes the new password, updates it, revokes all sessions, and consumes
    the action;
  - an invalid action causes no password/session mutation.

### Current profile and shared sign-in rule

- `GetCurrentUserUseCase`
  - native UUID actor data overrides OIDC claim fallbacks;
  - non-UUID OIDC subjects skip native lookup and use safe claim fallbacks;
  - granted permission values are returned in stable sorted order.
- `SignInGuard`
  - active users pass;
  - pending users receive `EMAIL_NOT_VERIFIED`;
  - disabled users receive `USER_DISABLED`.

## Verification gates

Run in this order:

```bash
./gradlew :trellis-identity:test
./gradlew :trellis-identity:check
./gradlew test
```

The focused test task must pass before the wider module and repository checks. Any mutation needed only to
make a test green must be reviewed as a production bug, not hidden by weakening the assertion.
