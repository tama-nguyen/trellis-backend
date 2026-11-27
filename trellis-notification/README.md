# trellis-notification: the sample module (copy this)

The template's living example of a business module. Every platform seam is used exactly once, so a new module
starts as a copy of this one with the names changed. Notifications are small announcements (`DRAFT -> PUBLISHED ->
ARCHIVED`, re-publishable) addressed to everyone (`ALL`) or to staff (`STAFF`).

## Layout (decision T17: fixed sub-packages, even when they hold one file)

```text
io.trellis.notification
  NotificationModule                 auto-configuration: scans application/, infrastructure/, web/
  api/
    dto/          NotificationAudience, NotificationSeverity, NotificationStatus      wire vocabulary (enums)
    event/        NotificationPublishedV1                                 payload of notification.published v1
    security/     NotificationPermissions                                 @PermissionCatalog
    error/        NotificationErrorCode, NotificationErrors                     error codes + exception factories
  domain/
    model/        Notification, NotificationContent                             immutable aggregate, transitions
    value/        NotificationCode                                        reference code NT-XXXX-XXXX-XXXX
    policy/       NotificationVisibility, NotificationReader                    who may see a notification (pure)
  application/
    command/      NotificationCommands                                    writes: tx, outbox, AOP stack
    query/        NotificationQueries, ListNotificationsQuery, NotificationAccess     reads: read-only tx, views
    port/         NotificationRepository, NotificationStatsRepository, NotificationFilter
    view/         NotificationView, NotificationSummary, NotificationStatsView, NotificationViews (mapper)
    validation/   NotificationContentValidator                            CommandValidator
  infrastructure/
    persistence/  JdbcNotificationRepository, JdbcNotificationStatsRepository   JdbcClient, version, keyset
    messaging/    NotificationStatsProjection, NotificationStatsRebuilder       EventHandler + ReadModelRebuilder
  web/
    rest/         NotificationController, NotificationContentRequest, NotificationPage
    graphql/      NotificationGraphQlController                           @QueryMapping / @BatchMapping
resources/
  db/changelog/notification/001-notification.sql       Liquibase (included from the app's master changelog)
  graphql/notification/notification.graphqls           this module's part of the GraphQL schema
  META-INF/spring/...AutoConfiguration.imports   registers NotificationModule
```

Skipped on purpose (no content yet): `infrastructure/client/` (the module calls no external service). Every
sub-package exists once it holds a class; ArchUnit enforces the layer rules
(`businessDomainIsFrameworkFree`, `businessApplicationDoesNotDependOnAdapters`, `businessWebStaysThin`,
`nothingDependsOnBusinessWeb`).

Use-case classes (decision T16): `<Aggregate>Commands` for writes and `<Aggregate>Queries` for reads. A use case
that grows large or distinct gets its own class named after it (`PublishNotification`, `SearchNotifications`) in the same
sub-package. Never one `<Aggregate>Service` for everything. These classes are the only place for
`@Transactional` and the AOP annotations.

## Which seam is where

| Seam | Where in this module |
| --- | --- |
| `RequestContext` first parameter | every public method of `NotificationCommands` / `NotificationQueries` |
| Validation pipeline (validate-all) | `NotificationContentValidator`, run by `NotificationCommands.create/update` |
| `@RequiresPermission` | every use case (`notification:read`, `notification:manage`) |
| Data-dependent authorization | `NotificationAccess` + `NotificationVisibility`: STAFF / draft notifications are 404 for readers who may not see them |
| `@RateLimited` | controller `createNotification` (30 per minute per actor) |
| `@Idempotent` | `publish`, key = notification code + `Idempotency-Key` header |
| `@Transactional` + `@Audited` | every write; audit actions `notification.created/updated/published/archived` |
| `@Observed` | every use case (`notification.create`, `notification.list`, ...) |
| Transactional outbox | `publish` appends `notification.published` v1 in the same transaction |
| Native SQL + optimistic lock | `JdbcNotificationRepository.update`: `WHERE id = :id AND version = :expectedVersion` |
| ETag / If-Match | `NotificationController` (`ETag: "<version>"`; stale `If-Match` = 412 `NOTIFICATION_VERSION_MISMATCH`) |
| Keyset pagination + opaque cursor | `JdbcNotificationRepository.page` (`created_at DESC, id DESC`), REST `first/after`, GraphQL `notifications(first, after)` |
| Reference code | `NotificationCode` over `ReferenceCode` / `ReferenceCodeGenerator` (trellis-common) |
| Read model, Java consumer | `NotificationStatsProjection` (consumer `notification-stats-projection`) -> `notification_stats` |
| Read model rebuild | `NotificationStatsRebuilder` (`POST /api/ops/read-models/notification-stats/rebuild`) |
| Python consumer of the same event | the `notification-stats` worker in the `trellis-ai` repo (consumer `notification-stats`) -> `notification_stats_counter` |
| GraphQL, N+1-free field | `NotificationGraphQlController` (`stats` via `@BatchMapping`, one query per request) |
| Error codes | `NotificationErrorCode` (`NOTIFICATION_NOT_FOUND`, `NOTIFICATION_INVALID_TRANSITION`, `NOTIFICATION_VERSION_MISMATCH`, `NOTIFICATION_INVALID_PRECONDITION`) |

## API

| REST (commands, integrations) | Notes |
| --- | --- |
| `POST /api/notifications` | 201, `Location`, `ETag` |
| `GET /api/notifications?first=&after=&audience=&query=` | `{items, nextCursor, hasNext}` |
| `GET /api/notifications/{code}` | `ETag` |
| `PUT /api/notifications/{code}` | `If-Match` required |
| `POST /api/notifications/{code}/publish` | optional `Idempotency-Key` |
| `POST /api/notifications/{code}/archive` | |

GraphQL (UI read models, `POST /api/graphql`): `notification(code)`, `notifications(first, after, audience, query)`, each with
`stats { publishCount firstPublishedAt lastPublishedAt }`. Both APIs call the same use cases; contracts are
exported to `contracts/openapi/openapi.json` and `contracts/graphql/schema.graphqls`.

Permissions and roles (seeded by `db/changelog/notification/002-notification-roles.sql`; realm roles in `build-logic/docker/keycloak/trellis-realm.json`):
`notification-reader` -> `notification:read`; `staff` -> + `notification:read-staff`; `notification-admin` -> + `notification:manage`.

## How to copy this module

1. Copy `trellis-notification/` to `<name>/` (one flat module directory per Gradle project, decision T25); rename packages `io.trellis.notification` ->
   `io.trellis.<name>` and the classes (`Notification*` -> `<Name>*`). Keep every sub-package (delete only the ones that
   would be empty, and say so in the README).
2. Add the module to `settings.gradle.kts` (`modules`) and as `implementation(project(":trellis-<name>"))`
   in `trellis-app/build.gradle.kts`.
3. Update `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` and the
   `@ComponentScan` packages of `<Name>Module`.
4. Liquibase: rewrite `db/changelog/<name>/001-<name>.sql` (changeset ids `trellis:<name>-NNN-...`) and include it
   at the end of `trellis-app/src/main/resources/db/changelog/db.changelog-schema.yaml`.
5. Permissions: rewrite `<Name>Permissions` (`<name>:<action>`) and map roles to them in
   `db/changelog/<name>/002-<name>-roles.sql`. A permission no catalog declares fails startup where it is
   used; one that no role maps to is simply a 403 on every call.
6. Events: write `contracts/events/<type>/v1.schema.json` first, then the payload record in `api/event`, the
   topic in `trellis.setup.topics`, and regenerate the published contract packages
   (`build-logic/scripts/contracts/generate.sh`; see `docs/architecture/contracts.md`).
7. GraphQL: rewrite `graphql/<name>/<name>.graphqls` (use `extend type Query`).
8. Export contracts and regenerate clients: `./gradlew :trellis-app:exportApiContracts`, then `cd web && pnpm
   codegen` and `cd workers && uv run python ../contracts/codegen/python/generate_sdk.py`. Commit the output.
9. Delete what the new module does not need (the read model, the rebuilder) rather than leaving dead copies.
10. Run `./gradlew build` (ArchUnit, context load, config classification, contract drift).

Tests (decision T5): only `NotificationFlowTest` in `trellis-app` (the template's reference flow: REST publish -> outbox ->
relay -> projection exactly once, idempotent replay, STAFF 404, GraphQL stats). A copied module does not copy
that test unless the owner asks for feature tests.
