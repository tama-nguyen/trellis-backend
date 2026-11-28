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

### The `setup` and `migrate` profiles are replaced by one switch

- **What changed:** `application-setup.yml` and `application-migrate.yml` are gone. Set
  `TRELLIS_SETUP_ENABLED=true` to run the one-shot job; it applies the database changes and prepares
  Kafka topics and the storage bucket in one run. The old profile names are
  now ignored without a warning, so an app started with only `prod,setup` serves requests instead of
  running the job.
- **Who is affected:** Anyone whose deployment job or local script uses `dev,setup`, `prod,setup` or
  `prod,migrate`.
- **What to do:** Select only `dev`, `test` or `prod`. Add `TRELLIS_SETUP_ENABLED=true` and
  `SPRING_MAIN_WEB_APPLICATION_TYPE=none` to the setup job and delete the separate migrate job.

### The data backfill framework is removed

- **What changed:** The app no longer has resumable data backfills that run in the setup job or hold back
  startup. The `migration_step_checkpoint` table is no longer created, and every `trellis.migration.*`
  setting (`TRELLIS_MIGRATION_*`) is gone. No backfill had been written yet.
- **Who is affected:** Anyone who set one of those settings, or kept a local database created earlier.
- **What to do:** Remove the settings. An older local database keeps an unused, empty table; it does no
  harm and disappears when the database is recreated.

### Production startup requires explicit choices

- **What changed:** The serving app no longer changes the database schema in production; only the setup
  job does. `TRELLIS_EVENTS_TRANSPORT` has no production default. An empty `TRELLIS_CURSOR_SECRET`
  stops startup.
- **Who is affected:** Anyone running a production deployment, or a script that uses another profile name
  such as `local`, which has no settings of its own.
- **What to do:** Run the setup job before each release, set the transport to `kafka` or `inmemory`,
  provide a cursor secret of at least 16 bytes, and select one of `dev`, `test` or `prod`.

### Generated Long entity IDs require a fresh local environment

- **What changed:** Native users, passwords, sessions, action tokens, mail jobs, courses and
  notifications now use BIGINT row IDs and repository-based inserts. Native actor and resource-scope
  IDs are canonical positive decimal strings. Public course/notification reference codes and technical
  event/lease UUIDs remain unchanged. Existing audit/role-grant numeric int64 wire IDs are unchanged.
- **Who is affected:** Developers with the previous local schema, saved sessions/cursors, actor-based
  Redis quotas or Kafka events/inboxes tied to the old environment. Sequential IDs are predictable;
  a service key remains a credential with user-delegation authority.
- **What to do:** This owner-approved pre-production change edits original Liquibase definitions.
  Recreate only the intended disposable PostgreSQL environment and remove/isolate its dependent
  Redis/Kafka/worker state before starting writers. Clear browser sessions. Never use clearCheckSums
  to reuse the incompatible schema. Follow ADR 0004 and the Long-ID verification guide. This is not
  a migration or downgrade procedure for production/shared databases; those need a separate design.

### The sample module "notice" is now "notification"

- **What changed:** The sample feature was renamed everywhere. The module is `trellis-notification`
  (Java package `io.trellis.notification`). REST paths moved from `/api/notices` to
  `/api/notifications`; GraphQL `notice`/`notices` are now `notification`/`notifications`; the field
  `noticeCode` is now `notificationCode`; the event `notice.published` is now
  `notification.published`; permissions `notice:*` are now `notification:*`; roles `notice-reader` and
  `notice-admin` are now `notification-reader` and `notification-admin`; error codes `NOTICE_*` are
  now `NOTIFICATION_*`; the setting `trellis.features.notice-search` is now `notification-search`;
  database tables and the `notice-stats` worker follow the same pattern. Reference codes keep the
  `NT-` prefix.
- **Who is affected:** Every client of the notice API or event, anyone holding the old roles, and
  every existing database, because the original database changesets were edited in place.
- **What to do:** Create a new, empty database (an existing one cannot be upgraded) and re-create the
  local Kafka topics and Keycloak realm. Deploy the backend, web app and workers together, and rename
  any role grants, settings and worker configuration that use the old names.

### Course reads use REST

Clients querying GraphQL fields `course` or `courses` must switch to authenticated
`GET /api/courses/{code}` or `GET /api/courses`. The list accepts `first`, `after`,
`status` and `query`. Read the page from the JSON response body and pass its `nextCursor`
as `after` when `hasNext` is true. Missing and hidden courses return HTTP 404.

### New role grants validate their targets

- **What changed:** New grants require an existing native USER UUID or a matching encoded external
  USER/SERVICE ID. Non-global scope types must have a registered lookup and reference an existing
  resource; global is exactly `(*, *)`. The current resource lookup is `course` by aggregate UUID.
- **Who is affected:** Operator integrations granting to missing native users/resources, raw external
  subjects, unregistered scope types or partial wildcard scopes. Previously stored grants remain.
- **What to do:** Create native users/resources first, derive external IDs with
  `OidcPrincipalIdEncoder`, and register resource lookups with their authorization consumer.
  Invalid targets return 400 or 404. Existing legacy data needs a separately reviewed migration;
  follow the manifest/dry-run procedure in `docs/guide/security-remediation.md`.

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

## Security remediation — identity keys, course policy and mail queue

OIDC principal IDs are now issuer/type-namespaced keys. Native UUIDs remain. Existing external
role grants, course/notice ownership and pending event/idempotency data require explicit provenance
mapping and coordinated cutover described in docs/guide/security-remediation.md.

JWT authentication with explicit JWKS also requires issuer-uri. Course creation requires global
course:manage. Update accepts resource-scoped manage grants. Course list includes scoped READ under
the same visibility rule as detail. Permission caches and their grants.cache-* settings are removed.

Production requires TRELLIS_IDENTITY_MAIL_ENCRYPTION_KEY. User-action emails are queued transactionally
and delivered asynchronously. Setup/migrate/test disable workers. New action issuance supersedes old
actions, and the schema migration retires duplicate outstanding actions before adding a unique index.
Security outcome audit adds security_event while successful identity mutations use audit_event.

## OIDC principal encoder extraction

Java callers of Actor.oidcUser/oidcService now use OidcPrincipalIdEncoder.userId/serviceId from
trellis-platform/security/jwt and the generic Actor factories. User creation can explicitly supply
IdentityOrigin through the four-argument Actor.user overload. The three-argument factory retains
its native default. Fixed-vector tests preserve the existing principal ID format, including its
UTF-16 issuer-length prefix. Persisted identity values require no additional migration for this extraction.
