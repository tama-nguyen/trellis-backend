# 0002. Keep resource authorization inside Trellis

- Status: accepted; authentication ownership partially superseded by ADR 0003
- Date: 2026-11-23

## Context

The identity provider authenticates users and services and issues coarse roles. Trellis previously flattened
roles and direct permissions into one set, so a role whose name looked like a permission could bypass the
role-to-permission mapping. Authorization was also global: holding `course:manage` meant managing every
course, even when the business rule concerned one course.

Trellis needs one answer to “may this subject perform this action on this resource?” without teaching the
platform what a course or any future business resource means. The answer must remain consistent in single-row
commands, paged list queries, metrics and audit records.

## Decision

The identity provider owns authentication and coarse role assignment. It never owns Trellis permissions.
A role is a row in `role` with an id of its own and an optional `tenant_id`, so two tenants can define the
same name differently. Trellis maps those roles to declared permissions and stores grants as
`(subject, role, scope)`. A scope is an opaque
`(type, id)` pair to the platform; `(*, *)` is a global grant.

Every authorization decision receives a scope explicitly. There is no overload that silently assumes global
scope. The final authorization service denies by default, always denies anonymous actors and always permits the
system actor in an exhaustive branch that custom implementations cannot replace.

Business code loads the addressed aggregate before making a data-dependent authorization decision. A caller
who may not see an aggregate receives 404, so its existence is not disclosed. A caller who may see it but may
not perform the requested mutation receives 403. Paged queries push scoped-grant visibility into SQL; they do
not post-filter a page.

Tenant enforcement is deferred until Trellis has a real tenant boundary. Personal access tokens and a general
ABAC/OPA policy engine are outside this decision. ADR 0003 later introduces native accounts as an explicit
product requirement while keeping the authorization boundary decided here.

## Consequences

- Roles and direct permissions keep their provenance in `Actor`; only declared direct permissions are accepted.
- Role mappings and scoped grants live in PostgreSQL and use one runtime resolution path.
- Business modules name scope types but the platform owns grant storage, caching and the SQL grant predicate.
- Global authorization is still possible, but every call site must choose it explicitly.
- Grant-aware list queries remain correctly paged and do not disclose hidden row counts.
- The owner did not approve the temporary T5 feature-test exception. Steps 3 and 4 therefore ship without the
  proposed `CourseAccessIT`; the existing platform and reference-flow tests remain mandatory. This leaves a
  known regression risk around the 404/403 boundary and scoped page visibility until T5 is revisited.

