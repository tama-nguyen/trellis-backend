# Review: phần authorization đang trong stage

Jira: [TL-117](https://tama-nguyen.atlassian.net/browse/TL-117)

Phạm vi: các file chưa commit của Bước 1–5 (ADR 0002 → access grant API). **Không** xét
`../../trellis-identity` vì đang làm giữa dở.

Đọc theo thứ tự: mục 1 là lỗi đúng/sai, mục 2 là vi phạm convention đã ghi trong repo, mục 3 là
file thừa và đặt tên, mục 4 là `RolePermissionBootstrapStep` (phần bạn hỏi riêng), mục 5 là
những chỗ làm đúng — để không sửa oan.

---

## 0. Trạng thái sau lượt sửa (cập nhật)

| Mục | Trạng thái |
| --- | --- |
| 1.1 hai clock cho expiry | **đã sửa** — `GrantPredicate` nhận `GrantSubject.asOf()`, bỏ `now()` trong SQL |
| 1.2 hai enum subject type | **đã sửa** — `GrantSubject.of(Actor, Instant)` là chỗ duy nhất map; `CourseFilter` mang `GrantSubject`, không mang `ActorType` |
| 1.3 đường role không cache | **đã sửa** — `GrantCache.byRoles(...)` |
| 1.4 mất fail-fast lúc startup | **đã sửa** — `DeclaredPermissions` bỏ qua + log warn giá trị sai ở cả hai nguồn; `RolePermissionVerifier` quét cả bảng lúc khởi động |
| 1.5 seed test trong changelog prod | **đã sửa** — role → permission giờ là changeset của từng module, chạy ở mọi môi trường; không còn property YAML nào |
| 2.1 `IllegalArgumentException` | **đã sửa** — `PlatformErrorCode` + `TrellisException` |
| 2.2 `MigrationStep` dùng sai | **đã sửa** — bỏ hẳn lớp bootstrap; seed là Liquibase, `SetupRunner` thôi gọi `migrations.run(true, true)` |
| 2.3 include giữa master changelog | **đã sửa** — cả hai include xuống cuối section |
| 2.4 `java.util.Optional` FQN | **đã sửa** — biến mất cùng `GrantSubject` |
| 2.5 port trộn đọc/CRUD + cache trong repo | **chưa** — cần quyết hướng |
| 2.6 hai bề mặt operator | **chưa** — cần quyết hướng |
| 2.7 enum nội bộ trong contract | **chưa** — cần quyết hướng |
| 3.1 ba vocabulary | **đã sửa** — tất cả là `RoleGrant`: `/api/platform/role-grants`, `platform.role-grants:manage`, `platform.role-grant.granted` |
| 3.2 Operations gom hai aggregate | **đã sửa** — `RoleGrantOperations` + `AuditEventOperations`, `RoleGrantController` + `AuditEventController` |
| 3.3 bốn bản copy record page | **chưa** — `CursorPageResponse<T>` chung, cần kiểm tra springdoc trước |
| 3.4 `DEFAULT_FIRST` hai nơi | **đã sửa** — `PageSize.DEFAULT_TEXT` / `MAX` / `require` |
| 3.5 audit đọc nằm trong package aspect | **đã sửa** — `io.trellis.platform.audit` |
| 3.6 ba class Actor authentication | **giữ** — `ActorBearerAuthenticationToken` đã có người dùng (`trellis-identity`) |
| 3.7 nổ tổ hợp boolean trong CourseFilter | **đã sửa** — sáu biến thể SQL còn ba OR-term ghép bằng `String.join` |
| 3.8 GrantPredicate thiếu param | **đã sửa** — fragment trả đủ sáu param, không còn hằng public |
| 1.6 EXISTS tương quan theo từng hàng (phát hiện sau) | **đã sửa** — tách thành hai mệnh đề **không tương quan**; đo trên 100k course + 60k grant: 4.2 s → 18 ms, và hai dạng trả về tập hàng giống hệt nhau trên 5 ca kiểm thử |

**Ghi chú về index:** `ix_role_grant_subject` đã bỏ theo yêu cầu. Tra cứu grant giờ dựa vào index của
`uq_role_grant`, khoá này mở đầu bằng `(subject_type, subject_id, ...)` nên Postgres vẫn dùng được nó cho
mệnh đề `WHERE subject_type = ? AND subject_id = ?` của `permissionsForSubject` và `GrantPredicate`. Cái
mất đi là lọc theo `scope_type`/`scope_id` khi không biết subject - hiện không có truy vấn nào như vậy ngoài
API quản trị, vốn hiếm.

**Ghi chú về kiểm tra permission sai chính tả:** đã đóng bằng hai lớp, sau khi tìm ra lỗi nặng hơn mô tả
ban đầu - `JdbcRoleGrantStore.permissions` gọi thẳng `Permission.of`, nên một dòng **sai định dạng**
(`Notification_Read`) ném `IllegalArgumentException` **giữa lúc quyết định authorization**: 500 cho mọi request
của mọi actor giữ role đó, không phải 403 im lặng.

1. `DeclaredPermissions` là chỗ duy nhất biến chuỗi ngoài code thành `Permission`: bỏ qua giá trị sai định
   dạng hoặc không được `@PermissionCatalog` khai, kèm log warn có `source`. Dùng cho **cả hai** nguồn
   không compile-check được - claim trong token và dòng `role_permission` - nên hai nguồn không còn hai luật.
   Bỏ qua là an toàn: permission không được khai thì không bao giờ thoả một requirement, vì requirement dựng
   từ hằng của catalog.
2. `RolePermissionVerifier` quét `SELECT DISTINCT permission FROM role_permission` lúc khởi động, cạnh
   `PermissionUsageVerifier` sẵn có, và **log warn** chứ không fail - rollback về bản cũ hơn sẽ gặp đúng
   tình huống đó một cách hợp lệ, chặn khởi động thì biến một lệch cosmetic thành outage.

---

## 1. Lỗi đúng/sai

### 1.1. Hai cái clock cho cùng một luật hết hạn  — nặng nhất

[GrantPredicate.java:21](../../../trellis-platform/src/main/java/io/trellis/platform/security/authz/sql/GrantPredicate.java#L21)
dùng `now()` của **database**:

```sql
AND (rg.expires_at IS NULL OR rg.expires_at > now())
```

còn [JdbcRoleGrantStore.java](../../../trellis-platform/src/main/java/io/trellis/platform/security/authz/JdbcRoleGrantStore.java)
`permissionsForSubject` nhận `Instant now` từ `Clock` được inject.

Hệ quả: cùng một grant sắp hết hạn có thể **còn sống ở list và đã chết ở single-row**, hoặc
ngược lại — tuỳ lệch đồng hồ giữa app và Postgres. Và `MutableClock` trong test không điều khiển
được nhánh SQL, nên không test được biên hết hạn ở list.

AGENTS.md: *"Time comes from the injected `java.time.Clock`; `Instant.now()` and friends are
banned by ArchUnit."* `now()` trong SQL là đúng cái lỗ hổng đó, chỉ là ArchUnit không thấy.

**Fix:** `scopedGrantExists(...)` nhận `Instant now` và bind `:grantNow`. Người gọi truyền
`ctx.requestTime()`. Cân nhắc thêm một ArchUnit/Spotless check cấm `now()` trong text block SQL.

### 1.2. Hai enum cho cùng một khái niệm, và một cái đi thẳng vào CHECK constraint

- `GrantSubjectType` (USER, SERVICE) — [GrantSubjectType.java](../../../trellis-platform/src/main/java/io/trellis/platform/security/authz/GrantSubjectType.java)
- `ActorType` (USER, SERVICE, SYSTEM, ANONYMOUS) — đã có trong `trellis-common`

`CourseFilter` mang `ActorType subjectType`, rồi
Historical source `JdbcCourseRepository.java:118`
bind `filter.subjectType().name()` vào `rg.subject_type`, cột có
`CHECK (subject_type IN ('USER','SERVICE'))`. Còn nhánh single-row thì bind
`GrantSubjectType.name()`.

Nghĩa là: hai đường cùng ghi/đọc một cột, qua hai enum khác nhau, và đường đi qua `ActorType`
có thể mang `SYSTEM`/`ANONYMOUS` — khớp **không** hàng nào, im lặng. `DbPermissionResolver` phải
có một hàm `subjectType()` chỉ để dịch giữa hai enum.

**Fix:** giữ một enum. Đơn giản nhất là bỏ `GrantSubjectType`, dùng `ActorType` ở cả hai đường,
và chặn SYSTEM/ANONYMOUS ở chỗ ghi (một `switch` exhaustive trả về `Optional`, như resolver đang
làm) chứ không để nó trôi xuống SQL. Nếu muốn giữ hai enum thì `CourseFilter` phải dùng
`GrantSubjectType`, không phải `ActorType`.

### 1.3. Đường đọc permission theo role **không** được cache

Historical source `DbPermissionResolver.java:25`:

```java
permissions.addAll(grants.permissionsForRoles(actor.roles()));   // mỗi lần decide = 1 query
subjectType(actor).ifPresent(... cache.get(...) ...);            // chỉ phần này có cache
```

`permissionsForRoles` là đường **phổ biến nhất** (mọi permission toàn cục của mọi request đi qua
đây), và nó query DB mỗi lần `decide`. Một request gọi `@RequiresPermission` rồi thêm vài
`require(...)` phụ thuộc dữ liệu sẽ bắn 3–5 query chỉ để hỏi lại cùng một thứ.

**Fix:** cache cả hai, hoặc cache ở mức `(roles)` riêng. Cache key hiện tại là
`(subjectType, subjectId, scope)` nên không phục vụ được role-only; thêm một entry
`Key(null, rolesHash, GLOBAL)` thì rẻ hơn là query.

### 1.4. Mất fail-fast lúc startup cho config sai

Bản cũ validate ở `SecurityAutoConfiguration.permissionResolver`: config grant một permission
không khai trong `@PermissionCatalog` → **context không lên**. Bản mới chuyển việc đó vào
`RolePermissionBootstrapStep.runBatch`, tức là chỉ nổ khi chạy `setup`/`migrate`.

Và vì schema không có bảng `permission` + khoá ngoại, **không gì** kiểm tra các dòng
`role_permission` do người khác `INSERT` tay: một typo `cuorse:manage` nằm im trong DB, và
permission đó vĩnh viễn không bao giờ khớp.

**Fix (một trong hai, nên làm cả hai):**
1. Thêm bảng `permission` được seed từ `PermissionRegistry`, và
   `role_permission.permission REFERENCES permission(name)`. DB tự chặn, đúng cả khi INSERT tay.
2. Trả lại validate lúc startup cho `bootstrap.role-permissions` (một `@PostConstruct` hoặc
   `SmartInitializingSingleton` cạnh `PermissionUsageVerifier` đã có sẵn cho `@RequiresPermission`).

### 1.5. Seed data của test nằm trong changelog production

[001-access-grant.sql](../../../trellis-platform/src/main/resources/db/changelog/platform/security/001-access-grant.sql)
changeset `security-003-test-role-permission context:test` chèn thẳng bảng role→permission cho
`platform-admin`, `notification-reader`, `staff`, `notification-admin`.

Ba vấn đề:

- Cùng một dữ liệu giờ có **hai đường nạp**: YAML → `RolePermissionBootstrapStep`, và SQL cứng
  trong changelog. Plan đã chốt "một quyết định, một code path" chính vì hai đường thì sẽ lệch —
  và đây là lệch ngay trong lần đầu: sửa `application-test.yml` sẽ không ảnh hưởng gì tới test.
- Tên role là magic string, giờ xuất hiện ở ba chỗ: `application-dev.yml`, `application-test.yml`,
  và file SQL này.
- Nó tồn tại để vá một lỗ hổng thật: *test boot profile serving mà không chạy `setup`* →
  bảng rỗng → mọi thứ 403. Vá bằng Liquibase context là chữa triệu chứng.

**Fix:** xoá changeset 003. Cho test nạp seed qua cùng đường như môi trường thật — gọi
`MigrationRunner` trong test setup, hoặc một `@TestBean`/`@ServiceConnection`-style fixture chạy
`RolePermissionBootstrapStep` một lần. Nếu muốn giữ SQL cho test thì nó phải nằm trong
`src/test/resources`, không phải trong changelog mà prod cũng đọc.

---

## 2. Vi phạm convention đã ghi trong repo

### 2.1. `IllegalArgumentException` thay cho `ErrorCode`

Historical source `PlatformAuthorizationOperations.java`
ba chỗ:

```java
throw new IllegalArgumentException("occurredFrom must be before occurredTo");
throw new IllegalArgumentException("first must be between 1 and " + MAX_FIRST);
throw new IllegalArgumentException("expiresAt must be in the future");
```

AGENTS.md: *"Errors: throw a `TrellisException` subclass with an `ErrorCode`"* và *"Validation of
business rules runs in the application layer through `ValidationPipeline`"*.

**Fix:** một `PlatformErrorCode` (hoặc dùng `ValidationPipeline` + `CommandValidator` như
`NotificationContentValidator`), để lỗi ra RFC 7807 có `errorCode` thay vì rơi vào mapper mặc định.

### 2.2. `MigrationStep` bị dùng sai hợp đồng

Xem mục 4.

### 2.3. Include chèn vào giữa `db.changelog-master.yaml`

AGENTS.md: *"included at the **end** of `db/changelog/db.changelog-master.yaml`"*. Hai include mới
chèn vào giữa (security trước events; identity trước notification). Với DB mới thì vô hại, nhưng quy
ước tồn tại để thứ tự changeset không đổi giữa các môi trường.

**Fix:** đưa include mới xuống cuối khối tương ứng.

### 2.4. `java.util.Optional` viết fully-qualified trong code

Historical source `DbPermissionResolver.java`
`private static java.util.Optional<GrantSubjectType> subjectType(...)` và
`java.util.Optional.of(...)`. Không có xung đột tên nào để biện minh; cả repo đều import.

### 2.5. Port trộn đường đọc authorization với CRUD quản trị, và repository tự xoá cache

[RoleGrantStore.java](../../../trellis-platform/src/main/java/io/trellis/platform/security/authz/RoleGrantStore.java)
có 5 method: 2 cho đường quyết định (`permissionsForRoles`, `permissionsForSubject`) và 3 cho API
quản trị (`grant`, `revoke`, `page`). `DbPermissionResolver` chỉ cần 2.

Nặng hơn: [JdbcRoleGrantStore.java:130,138](../../../trellis-platform/src/main/java/io/trellis/platform/security/authz/JdbcRoleGrantStore.java#L130)
gọi `cache.invalidateAll()` **trong repository**. Một repository không nên sở hữu vòng đời cache;
nó cũng làm `grant()` không thể dùng lại ở chỗ không có cache.

**Fix:** tách `PermissionMappingStore` (đường đọc, resolver dùng) khỏi `RoleGrantAdminStore`
(CRUD). Việc invalidate chuyển lên application service (`AccessGrantCommands`), nơi đã có
transaction — và nên invalidate sau commit, không phải giữa transaction.

### 2.6. Bề mặt operator thứ hai, không theo tiền lệ `/api/ops`

Repo đã có `/api/ops` với `OpsGate` + feature flag `opsApi`
([OpsController.java](../../../trellis-app/src/main/java/io/trellis/app/ops/OpsController.java)). Code mới
mở `/api/platform` — root thứ hai, không gate, không flag.

**Fix:** hoặc đưa vào `/api/ops/...` theo cùng cấu trúc, hoặc nếu cố ý tách thì nói rõ trong ADR
vì sao bề mặt này không cần `opsApi` flag (lập luận "nó đã có `@RequiresPermission`" là hợp lý —
nhưng phải ghi ra, và lúc đó nên xét bỏ `OpsGate` cho nhất quán).

### 2.7. Enum nội bộ của platform trở thành một phần contract công khai

`AccessGrantRequest`/`AccessGrantView` phơi `GrantSubjectType` ra OpenAPI. AGENTS.md: wire
vocabulary (enum chia sẻ với client) thuộc `api/dto` của module sở hữu, không phải một enum nằm
sâu trong `platform.security.authz`.

**Fix:** một enum DTO riêng ở tầng web của app, map sang enum nội bộ — hoặc chuyển enum lên
`trellis-common` nếu nó thật là vocabulary chung (và lúc đó mục 1.2 giải quyết luôn: đó là
`ActorType`).

---

## 3. File thừa và đặt tên

### 3.1. Ba từ cho một khái niệm

| Chỗ | Từ đang dùng |
| --- | --- |
| bảng, record, port | `role_grant`, `RoleGrant`, `RoleGrantStore`, `RoleGrantFilter` |
| DTO web | `AccessGrantRequest`, `AccessGrantView`, `AccessGrantPage` |
| permission, route, audit action | `platform.access:manage`, `/access/grants`, `platform.access.granted` |

Một feature, ba vocabulary: *role-grant*, *access-grant*, *access*. Người đọc phải tự đoán
`AccessGrantView` là view của `RoleGrant`.

**Fix:** chọn một. Đề nghị **`RoleGrant`** xuyên suốt, vì nó mô tả đúng cái được lưu (một *role*
tại một scope), còn "access" là cái nó tạo ra: `RoleGrantRequest/View/Page`,
`platform.role-grants:manage`, `/role-grants`, `platform.role-grant.granted`. Nếu thích
"access" hơn thì đổi cả bảng và record — nhưng không để cả hai.

### 3.2. `PlatformAuthorizationOperations` gom hai aggregate

Tên `*Operations` là **đúng** tiền lệ của app module (`EventOperations` đã có). Vấn đề là phạm
vi: một class đang giữ audit-event query + role-grant query + grant + revoke, tức hai aggregate
không liên quan, và T16 bảo use case lớn/khác nhau thì tách class.

**Fix:** `RoleGrantOperations` và `AuditEventOperations`. Controller cũng tách theo
(`RoleGrantController`, `AuditEventController`) — `PlatformAuthorizationController` đang là tên
của hai thứ ghép lại.

### 3.3. Bốn bản copy của cùng một record phân trang

`AccessGrantPage`, `AuditEventPage` (mới) + `NotificationPage`, `CoursePage` (cũ) — cùng một
`{items, nextCursor, hasNext}` và cùng một `of(CursorPage<T>)`.

**Fix:** một `CursorPageResponse<T>` trong `trellis-platform-web` (`web/rest`), đúng chỗ
`VersionETags` vừa được dọn về ở Bước 1 — cùng một lập luận, cùng một thư mục. Xoá được 2 file
mới và 2 file cũ. Kiểm tra springdoc sinh ra schema generic chấp nhận được trước khi chốt.

### 3.4. `DEFAULT_FIRST` hai nơi, một nơi chết

- `PlatformAuthorizationController.DEFAULT_FIRST = "50"` (String, dùng trong annotation)
- `PlatformAuthorizationOperations.DEFAULT_FIRST = 50` (public int, **không ai gọi**)

**Fix:** xoá hằng `public int` không dùng. `MAX_FIRST` thì giữ (nó là luật của application layer).
Nếu muốn một nguồn duy nhất thì để `MAX_FIRST`/`DEFAULT_FIRST` ở application và annotation dùng
chuỗi hằng riêng với comment trỏ sang — annotation cần compile-time constant, không tránh được.

### 3.5. Kiểu query audit nằm trong package của aspect

`AuditEventFilter`, `AuditEventRecord`, `AuditEventStore`, `JdbcAuditEventStore` nằm ở
`io.trellis.platform.aspect.audit` — package của `@Audited` aspect (đường **ghi**). Giờ nó chứa
cả API **đọc** cho operator.

**Fix:** `io.trellis.platform.audit` cho đường đọc; aspect giữ phần ghi. `aspect/` nên chỉ chứa
AOP plumbing, đúng như `package-info` của nó nói.

### 3.6. Ba class cho một khái niệm "authentication mang Actor"

`ActorAuthentication` (interface) + `ActorJwtAuthenticationToken` + `ActorBearerAuthenticationToken`.
Cái thứ ba hiện **chưa ai dùng** trong phần authorization — nó thuộc `trellis-identity` đang làm.

Không đề nghị sửa, chỉ lưu ý: nếu `trellis-identity` bị bỏ thì xoá luôn
`ActorBearerAuthenticationToken`, và khi đó `ActorAuthentication` chỉ còn một implementation —
lúc đó interface là thừa.

### 3.7. `CourseFilter`: nổ tổ hợp boolean

[CourseFilter.java](../../../trellis-learning-content/src/main/java/io/trellis/learningcontent/application/course/port/CourseFilter.java)
giờ có `unrestricted`, `standardVisibility`, `scopedManage` + `subjectType`/`subjectId`, và
Historical source `JdbcCourseRepository.java:52-57`
có **sáu** biến thể SQL (`VISIBLE`, `VISIBLE_NO_OWNER`, `VISIBLE_SCOPED`,
`VISIBLE_SCOPED_NO_OWNER`, `VISIBLE_GRANT_ONLY`, `VISIBLE_NONE`) chọn qua if/else lồng ba tầng.

Đây là chỗ "chắp vá" rõ nhất về mặt đọc hiểu: ba boolean = tám tổ hợp, repository phải đúng từng
nhánh, và thêm một luật visibility nữa là nhân đôi lần nữa. Chú thích `@param scopedManage
whether a USER or SERVICE subject **may have** matching stored grants` cũng cho thấy filter đang
mang một phỏng đoán, không phải một sự kiện.

**Fix:** để application layer dựng một **danh sách điều kiện OR** thay vì các boolean cross-phụ
thuộc. Ví dụ `CourseFilter(Set<CourseStatus> visibleStatuses, String ownerId, GrantSubject
grantSubject, ...)` với `null` = không áp dụng, và repository ghép:

```java
List<String> orTerms = new ArrayList<>();
if (!filter.visibleStatuses().isEmpty()) orTerms.add(STATUS_IN);
if (filter.ownerId() != null)            orTerms.add(OWNER_IS);
if (filter.grantSubject() != null)       orTerms.add(grant.sql());
sql.append(orTerms.isEmpty() ? VISIBLE_NONE : " AND (" + String.join(" OR ", orTerms) + ") ");
```

Một đường ghép, không nhánh, thêm luật mới = thêm một phần tử. `unrestricted` vẫn giữ riêng vì
nó là "bỏ qua tất cả".

### 3.8. `GrantPredicate` bắt người gọi tự nhớ bind hai param

`scopedGrantExists(...)` trả `Fragment` chứa 3 param nhưng SQL cần 5; hai param còn lại
(`SUBJECT_TYPE_PARAM`, `SUBJECT_ID_PARAM`) người gọi phải tự `params.put` — và quên thì lỗi lúc
chạy, không phải lúc compile. Call site ở `JdbcCourseRepository` chứng minh điều đó: ba dòng
`params.put` rời rạc ngay sau `putAll(grant.params())`.

**Fix:** `scopedGrantExists(resourceIdColumn, permission, scopeType, subjectType, subjectId, now)`
trả về `Fragment` chứa **toàn bộ** param. Không còn hằng public nào cần phơi ra.

---

## 4. `RolePermissionBootstrapStep`

Bạn hỏi riêng file này, nên tách ra. Nó có bốn vấn đề, xếp theo mức độ.

### 4.1. Dùng sai hợp đồng `MigrationStep`

[MigrationStep.java](../../../trellis-platform/src/main/java/io/trellis/platform/persistence/migration/MigrationStep.java)
định nghĩa: *"One **resumable** data migration. The runner calls `runBatch` repeatedly, each call
inside its own transaction together with the checkpoint update, so a crash loses at most one
uncommitted batch."*

`RolePermissionBootstrapStep.runBatch` **bỏ qua tham số `checkpoint`**, làm toàn bộ công việc
trong một lần gọi, rồi trả `Done`. Nó không resumable, không batch, và nó là
**`MigrationStep` duy nhất trong repo** — nên nó đang vô tình trở thành *bản mẫu* cho mọi
MigrationStep sau này, dạy sai cách dùng.

Thêm nữa: `MigrationStep` nói về *data migration* (backfill dữ liệu đã có). Đây là *bootstrap
cấu hình* — một khái niệm khác, chạy mỗi lần `setup`, luôn idempotent, không có gì để resume.

### 4.2. `blocking() == true` nhưng chỉ nạp khi có ai chạy `setup`/`migrate`

`blocking()` nghĩa là *"serving app refuses traffic while any exist"*. Kết hợp lại:

- chạy `setup` → nạp xong → ổn;
- **không** chạy `setup` (ví dụ `./gradlew :trellis-app:bootRun` trên DB mới) → bảng rỗng → app
  từ chối traffic vì step chưa DONE, hoặc nếu nó DONE rỗng thì mọi request 403.

Đó chính là lý do changeset `context:test` ở mục 1.5 phải tồn tại. Hai triệu chứng, một nguyên
nhân: **bootstrap đang nằm sai chỗ.**

### 4.3. Lỗi cấu hình nổ ở thời điểm sai

```java
if (!known.isDeclared(permission)) {
    throw new IllegalStateException("...grants undeclared permission '" + permission + "'");
}
```

Đây là validate **cấu hình**, trước đây làm lúc dựng context (xem 1.4). Giờ nó nổ giữa một
migration — tức là sau khi Liquibase đã chạy, và chỉ ở môi trường nào tình cờ chạy `setup`.
`IllegalStateException` cũng không phải `TrellisException` có `ErrorCode`.

### 4.4. `processed` đếm sai ý nghĩa

`processed += ...update()` với `ON CONFLICT DO NOTHING` → lần chạy thứ hai báo `processed = 0`
dù đã kiểm tra N mapping. Log tiến độ sẽ nói "0 rows" ở mọi lần chạy lại, nhìn như không làm gì.

### 4.5. Nên viết lại thế nào

Bỏ `MigrationStep`. Bootstrap là việc của **setup mode**, cùng hạng với "tạo Kafka topic" và
"tạo S3 bucket" mà `SetupRunner` đang làm:

```java
// io.trellis.platform.security.authz.RolePermissionBootstrap  (không phải MigrationStep)
public final class RolePermissionBootstrap {
    /** Idempotent: upsert mapping từ cấu hình bootstrap. Trả về số mapping đã bảo đảm tồn tại. */
    public int apply();          // gọi từ setup mode
}
```

và:

1. **Validate lúc startup**, không phải lúc apply — một `SmartInitializingSingleton` cạnh
   `PermissionUsageVerifier` đã có, để typo trong YAML giết context như trước Bước 2.
2. **Thêm bảng `permission` + khoá ngoại** (mục 1.4) để DB tự chặn, kể cả INSERT tay.
3. `SetupRunner` gọi `bootstrap.apply()` tường minh, log số mapping — thay cho
   `migrations.run(true, true)` hiện tại. Hai boolean vị trí ở call site đó cũng là magic
   literal: đọc `migrations.run(true, true)` không ai biết nghĩa gì.
4. Đồng thời **`setup` mode không nên chạy mọi `MigrationStep`**. AGENTS.md phân vai rõ:
   `setup` = Liquibase + topics + bucket; `migrate` = Liquibase + `MigrationStep`s. Thay đổi ở
   [SetupRunner.java](../../../trellis-app/src/main/java/io/trellis/app/setup/SetupRunner.java) làm
   `setup` chạy cả backfill nghiệp vụ của mọi module — hai mode giờ làm cùng một việc, và tài
   liệu mô tả mode đã sai. Nếu muốn giữ thì phải sửa AGENTS.md và nói rõ vì sao.
5. Bỏ changeset `security-003-test-role-permission`; test gọi cùng `RolePermissionBootstrap`.

---

## 5. Những chỗ làm đúng, đừng sửa

Để review không bị đọc thành "viết lại hết":

- **`JwtActorMapper`** (trước là `ClaimActorMapper`) tách `roles()`/`permissions()`, lọc qua `PermissionRegistry`, log warn
  cho permission không khai và cho permission sai định dạng. Đúng D1, và log warn thay vì bỏ im
  lặng là lựa chọn đúng.
- **`DefaultAuthorizationService`** giữ `final`, giữ `switch` exhaustive, thêm tag `scope_type`
  mà **không** thêm `scope_id` — đúng như đã chốt, tránh cardinality explosion.
- **`ResourceScope` là tham số bắt buộc**, không có overload bỏ scope. Đây là phần quan trọng
  nhất của thiết kế và nó được giữ đúng.
- **Đẩy filter xuống SQL** thay vì post-filter: đúng kết luận của nghiên cứu, và `@NotNull
  boolean hasNext` trong các record page là style **có sẵn** của repo (`NotificationPage` cũng vậy),
  không phải lỗi mới.
- **`@Audited(aggregateId = "#result.id()")`** hợp lệ: annotation có hỗ trợ `#result`.
- **ADR 0002** viết tốt: có ranh giới IdP↔Trellis, luật 403/404, phần hoãn kèm lý do.

---

## 6. Thứ tự sửa đề nghị

```
1.1  một clock duy nhất cho expiry                (lỗi đúng/sai, rẻ)
1.2  một enum duy nhất cho subject type           (lỗi đúng/sai, rẻ)
1.4  bảng permission + FK, validate lúc startup   (khôi phục fail-fast)
4.5  viết lại bootstrap, bỏ MigrationStep         (kéo theo 1.5, 2.2)
1.5  xoá changeset context:test                   (sau khi 4.5 xong)
3.1  thống nhất một vocabulary                    (đổi tên hàng loạt, làm một lần)
3.2  tách Operations/Controller theo aggregate
3.7  bỏ nổ tổ hợp boolean trong CourseFilter
3.8  GrantPredicate trả đủ param
1.3  cache đường role                             (hiệu năng, sau khi đúng đã)
3.3  CursorPageResponse<T> dùng chung             (dọn, làm cuối)
3.5  tách audit đọc khỏi package aspect
2.1  ErrorCode cho validation
2.3  include xuống cuối master changelog
2.4  xoá java.util.Optional fully-qualified
```

Mục 2.5, 2.6, 2.7 cần bạn quyết hướng trước khi sửa (tách port, một hay hai bề mặt operator,
enum nào vào contract) — không phải việc cơ học.
