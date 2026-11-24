# trellis-identity

Native email-and-password identity (table `users`): registration, email verification, sign-in, opaque sessions with refresh
rotation, password reset. It sits **beside** the OIDC provider rather than replacing it — a request may arrive
with a Keycloak JWT or with a native bearer token, and both resolve to the same `Actor`
(decision in [`docs/adr/0003-native-identity-and-oidc.md`](../docs/adr/0003-native-identity-and-oidc.md)).

This module owns authentication only. **Authorization is not here**: what an actor may do comes from
`role_permission` and `role_grant`, owned by `trellis-platform`
([`docs/adr/0002-authorization-model.md`](../docs/adr/0002-authorization-model.md)).

## Layout

```text
io.trellis.identity
  IdentityModule                auto-configuration: scans application/, infrastructure/, web/
  IdentityProperties            token lifetimes, bcrypt strength, mail sender
  api/
    error/    IdentityErrorCode, IdentityErrors      wire codes + exception factories
  domain/
    user/     User, UserStatus, UserCredentials,
              EmailAddress, UserSignIn               native user and sign-in rules
    session/  UserSession                             refreshable native session
    token/    TokenType                               opaque secret families
    action/   UserActionToken, UserActionType         email verification and password reset
  application/
    usecase/  authentication/, registration/,
              password/, profile/                    one class and execute method per use case
    support/  ActionTokens, SignInGuard               shared application rules
    port/     UserRepository, PasswordHasher, TokenCodec, UserMailSender
    view/     AuthTokens, AuthenticatedUser
  infrastructure/
    persistence/
      user/       JPA adapter, user/password entities and repositories
      session/    session entity and repository
      action/     one-time action-token entity and repository
    security/     BcryptPasswordHasher, SecureTokenCodec, NativeTokenAuthenticationProvider
    mail/         SmtpUserMailSender
  web/rest/   AuthController                          /api/auth/*, one endpoint per use case
    dto/      LoginRequest, RegisterRequest, ...     request payloads, shape validation only
resources: db/changelog/identity/001-native-identity.sql
```

## Tables

`users`, `user_password`, `user_session`, `user_action_token` - plural, because `user` is a reserved word in
PostgreSQL. The password hash lives in its own
table so an account read never carries the credential. Sessions and action tokens store **only** a SHA-256 hash
of the secret: a database dump cannot be replayed as a token.

## Dev accounts

`db/changelog/identity/002-dev-users.sql` seeds `reader@example.test`, `staff@example.test` and
`admin@example.test` (passwords `reader-dev`, `staff-dev`, `admin-dev`) from `db.changelog-dev.yaml` only,
mirroring the Keycloak personas so both providers offer the same three roles. The same file grants them their
roles in `role_grant`, because a native token carries only `trellis.identity.default-role` — everything else a
native account may do is a stored grant. Details: [`docs/architecture/credential.md`](../docs/architecture/credential.md).

## What a copy of this base should check first

- `trellis.identity.*` in `application.yml`: token lifetimes, bcrypt strength, and the from-address.
- Whether native sign-in should exist at all. A product that only uses the IdP can drop this module from
  `settings.gradle.kts` and from `trellis-app`'s dependencies; nothing else depends on it.
