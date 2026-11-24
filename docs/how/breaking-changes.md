# Breaking changes

Every change that breaks something a user of Trellis (or of a product generated from it) relies on gets
an entry here, newest first. Write it for someone who does not read code: what changed, who is affected,
what they have to do. This file is also the source for the changelog.

A change is breaking when it changes a public API or contract, renames or removes a configuration key or
environment variable, changes a URL, or requires a manual migration step.

## Template

```markdown
## Next release

### <Short title>

- **What changed:**
- **Who is affected:**
- **What to do:**
```

## Next release

### Configuration classes use Spring field injection

- **What changed:** Configuration records have been replaced by classes using Spring `@Value` on fields.
  List settings, including `trellis.setup.topics`, use comma-separated scalar values in YAML.
- **Who is affected:** Custom deployment files using YAML sequences for Trellis list settings.
- **What to do:** Replace YAML sequences with comma-separated values, for example `topics: notice.published`.
  Property names and environment variable names are unchanged by this configuration refactor.

### Password reset now uses an emailed code

- **What changed:** `POST /api/auth/reset-password` now accepts `email`, `code`, and `newPassword` instead of
  `token` and `newPassword`. The configuration key is now `trellis.identity.reset-password-code-ttl`; the reset
  URI setting was removed.
- **Who is affected:** Native identity clients and deployments configuring the old password-reset token TTL or
  frontend reset URI.
- **What to do:** Prompt for the code sent by email, include the canonical account email in the reset request,
  rename `TRELLIS_IDENTITY_RESET_PASSWORD_TOKEN_TTL` to `TRELLIS_IDENTITY_RESET_PASSWORD_CODE_TTL`, and remove
  `TRELLIS_IDENTITY_RESET_PASSWORD_URI`.

### Token roles are no longer direct permissions

- **What changed:** Roles and direct permissions remain separate. A role such as `course:manage` grants nothing
  unless `role_permission` maps that role to a declared permission.
- **Who is affected:** Identity-provider configurations that put permission-shaped values in the roles claim.
- **What to do:** Keep only coarse role names in the roles claim and map them in the owning module's
  `role_permission` changeset.

### Production requires an OIDC audience

- **What changed:** Production startup fails when `trellis.security.audiences` is empty.
- **Who is affected:** Environments that configure an issuer or JWKS URI but no accepted audience.
- **What to do:** Set `TRELLIS_OIDC_AUDIENCES` to the resource-server audience issued for this API.

### Version ETag handling moved to the web platform

- **What changed:** Module-local `VersionETags` classes were removed. The shared parser now requires the module's
  invalid-precondition `ErrorCode` when parsing `If-Match`.
- **Who is affected:** Generated modules or custom modules that copied the old helper.
- **What to do:** Import `io.trellis.platform.web.rest.VersionETags` and call
  `VersionETags.parse(ifMatch, <Module>ErrorCode.<...>_INVALID_PRECONDITION)`.

### The authenticated Actor is created once

- **What changed:** `Actor.authorities()` and the two-argument user/service factories were removed. Actors now
  expose `roles()` and typed `permissions()` and are carried by the Spring authentication token.
- **Who is affected:** Custom authentication converters, actor factories and permission resolvers.
- **What to do:** Construct actors with separate role and permission sets and read the actor through
  `ActorResolver`.

### Role mappings moved from configuration to PostgreSQL

- **What changed:** `trellis.security.role-permissions` is gone, with no replacement property. Role-to-permission
  mapping now lives only in the `role_permission` table, seeded by each module's Liquibase changeset and changed
  afterwards with ordinary SQL.
- **Who is affected:** Every deployment with role mappings in application configuration.
- **What to do:** Delete the property, and move each mapping into a changeset of the module that declares the
  permission. Liquibase runs at startup, so nothing extra has to be run before serving traffic.

## Rate limit policies and production configuration

@RateLimited now requires a stable policy ID. Migrate permits/per declarations to registered
RateLimitPolicy beans and named configuration bandwidths. Production requires Redis, a dedicated
TRELLIS_RATE_LIMIT_KEY_SECRET and TRELLIS_RATE_LIMIT_NAMESPACE. Follow the maintenance cutover in
docs/guide/rate-limiting.md when migrating legacy buckets. Native authentication POST routes can
return 429 or 503 before DTO handling. Forgot-password account suppression returns 202.
