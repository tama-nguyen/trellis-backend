# trellis-notice: the sample module (copy this)

The template's living example of a business module. Every platform seam is used exactly once, so a new module
starts as a copy of this one with the names changed. Notices are small announcements (`DRAFT -> PUBLISHED ->
ARCHIVED`, re-publishable) addressed to everyone (`ALL`) or to staff (`STAFF`).

## Layout (decision T17: fixed sub-packages, even when they hold one file)

```text
io.trellis.notice
  NoticeModule                 auto-configuration: scans application/, infrastructure/, web/
  api/
    dto/          NoticeAudience, NoticeSeverity, NoticeStatus      wire vocabulary (enums)
    event/        NoticePublishedV1                                 payload of notice.published v1
    security/     NoticePermissions                                 @PermissionCatalog
    error/        NoticeErrorCode, NoticeErrors                     error codes + exception factories
  domain/
    model/        Notice, NoticeContent                             immutable aggregate, transitions
    value/        NoticeCode                                        reference code NT-XXXX-XXXX-XXXX
    policy/       NoticeVisibility, NoticeReader                    who may see a notice (pure)
  application/
    command/      NoticeCommands                                    writes: tx, outbox, AOP stack
    query/        NoticeQueries, ListNoticesQuery, NoticeAccess     reads: read-only tx, views
    port/         NoticeRepository, NoticeStatsRepository, NoticeFilter
    view/         NoticeView, NoticeSummary, NoticeStatsView, NoticeViews (mapper)
    validation/   NoticeContentValidator                            CommandValidator
  infrastructure/
    persistence/  JdbcNoticeRepository, JdbcNoticeStatsRepository   JdbcClient, version, keyset
    messaging/    NoticeStatsProjection, NoticeStatsRebuilder       EventHandler + ReadModelRebuilder
  web/
    rest/         NoticeController, NoticeContentRequest, NoticePage
    graphql/      NoticeGraphQlController                           @QueryMapping / @BatchMapping
resources/
  db/changelog/notice/001-notice.sql       Liquibase (included from the app's master changelog)
  graphql/notice/notice.graphqls           this module's part of the GraphQL schema
  META-INF/spring/...AutoConfiguration.imports   registers NoticeModule
```

Skipped on purpose (no content yet): `infrastructure/client/` (the module calls no external service). Every
sub-package exists once it holds a class; ArchUnit enforces the layer rules
(`businessDomainIsFrameworkFree`, `businessApplicationDoesNotDependOnAdapters`, `businessWebStaysThin`,
`nothingDependsOnBusinessWeb`).

Use-case classes (decision T16): `<Aggregate>Commands` for writes and `<Aggregate>Queries` for reads. A use case
that grows large or distinct gets its own class named after it (`PublishNotice`, `SearchNotices`) in the same
sub-package. Never one `<Aggregate>Service` for everything. These classes are the only place for
`@Transactional` and the AOP annotations.

## Which seam is where

| Seam | Where in this module |
| --- | --- |
| `RequestContext` first parameter | every public method of `NoticeCommands` / `NoticeQueries` |
| Validation pipeline (validate-all) | `NoticeContentValidator`, run by `NoticeCommands.create/update` |
| `@RequiresPermission` | every use case (`notice:read`, `notice:manage`) |
| Data-dependent authorization | `NoticeAccess` + `NoticeVisibility`: STAFF / draft notices are 404 for readers who may not see them |
| `@RateLimited` | controller `createNotice` (30 per minute per actor) |
| `@Idempotent` | `publish`, key = notice code + `Idempotency-Key` header |
| `@Transactional` + `@Audited` | every write; audit actions `notice.created/updated/published/archived` |
| `@Observed` | every use case (`notice.create`, `notice.list`, ...) |
| Transactional outbox | `publish` appends `notice.published` v1 in the same transaction |
| Native SQL + optimistic lock | `JdbcNoticeRepository.update`: `WHERE id = :id AND version = :expectedVersion` |
| ETag / If-Match | `NoticeController` (`ETag: "<version>"`; stale `If-Match` = 412 `NOTICE_VERSION_MISMATCH`) |
| Keyset pagination + opaque cursor | `JdbcNoticeRepository.page` (`created_at DESC, id DESC`), REST `first/after`, GraphQL `notices(first, after)` |
| Reference code | `NoticeCode` over `ReferenceCode` / `ReferenceCodeGenerator` (trellis-common) |
| Read model, Java consumer | `NoticeStatsProjection` (consumer `notice-stats-projection`) -> `notice_stats` |
| Read model rebuild | `NoticeStatsRebuilder` (`POST /api/ops/read-models/notice-stats/rebuild`) |
| Python consumer of the same event | the `notice-stats` worker in the `trellis-ai` repo (consumer `notice-stats`) -> `notice_stats_counter` |
| GraphQL, N+1-free field | `NoticeGraphQlController` (`stats` via `@BatchMapping`, one query per request) |
| Error codes | `NoticeErrorCode` (`NOTICE_NOT_FOUND`, `NOTICE_INVALID_TRANSITION`, `NOTICE_VERSION_MISMATCH`, `NOTICE_INVALID_PRECONDITION`) |

## API

| REST (commands, integrations) | Notes |
| --- | --- |
| `POST /api/notices` | 201, `Location`, `ETag` |
| `GET /api/notices?first=&after=&audience=&query=` | `{items, nextCursor, hasNext}` |
| `GET /api/notices/{code}` | `ETag` |
| `PUT /api/notices/{code}` | `If-Match` required |
| `POST /api/notices/{code}/publish` | optional `Idempotency-Key` |
| `POST /api/notices/{code}/archive` | |

GraphQL (UI read models, `POST /api/graphql`): `notice(code)`, `notices(first, after, audience, query)`, each with
`stats { publishCount firstPublishedAt lastPublishedAt }`. Both APIs call the same use cases; contracts are
exported to `contracts/openapi/openapi.json` and `contracts/graphql/schema.graphqls`.

Permissions and roles (seeded by `db/changelog/notice/002-notice-roles.sql`; realm roles in `build-logic/docker/keycloak/trellis-realm.json`):
`notice-reader` -> `notice:read`; `staff` -> + `notice:read-staff`; `notice-admin` -> + `notice:manage`.

## How to copy this module

1. Copy `trellis-notice/` to `<name>/` (one flat module directory per Gradle project, decision T25); rename packages `io.trellis.notice` ->
   `io.trellis.<name>` and the classes (`Notice*` -> `<Name>*`). Keep every sub-package (delete only the ones that
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

Tests (decision T5): only `NoticeFlowIT` in `trellis-app` (the template's reference flow: REST publish -> outbox ->
relay -> projection exactly once, idempotent replay, STAFF 404, GraphQL stats). A copied module does not copy
that test unless the owner asks for feature tests.
