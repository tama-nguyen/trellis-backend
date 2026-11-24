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
