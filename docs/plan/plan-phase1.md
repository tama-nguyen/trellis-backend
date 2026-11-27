# Phase 1 — Authorization: phân quyền vào DB, có scope theo resource

Kế hoạch cho use case đầu tiên của Trellis. Trạng thái: **Bước 0–5 đã thực thi; Bước 5A
(native identity + OIDC) đang triển khai; Bước 6 để lại**.

Quy ước dẫn chiếu [AGENTS.md](../../AGENTS.md) và [docs/architecture/backend.md](../architecture/backend.md);
không lặp lại luật ở đây.

**Tiền đề:** base được phép sửa, kể cả breaking. Trellis là source base, chưa product nào được
generate ra từ nó, `main` sạch. Đây là thời điểm **rẻ nhất** để sửa — mỗi product sinh ra sau
này là một bản copy của sai sót, lúc đó sửa là sửa N chỗ.

---

## 1. Vấn đề

### 1.1. Phân quyền đang nằm trong file config

```yaml
# application-dev.yml — nguồn sự thật của "role nào làm được gì"
role-permissions:
  platform-admin: [course:read, course:manage]
  notification-admin:   [notification:read, notification:read-staff, notification:manage]
```

Hệ quả: thêm một role cho khách hàng = sửa YAML + **redeploy**. Tenant không tự định nghĩa
được role. Không có lịch sử ai cấp quyền cho ai, lúc nào. Không thu hồi được quyền của một
người mà không ảnh hưởng mọi người cùng role. Với một LMS có org, instructor, co-instructor
thì mô hình này không trụ được.

### 1.2. Và không có phân quyền theo từng resource

- [CoursePermissions.java](../../trellis-learning-content/src/main/java/io/trellis/learningcontent/api/security/CoursePermissions.java)
  chỉ có `course:read`, `course:manage`, cả hai **toàn cục**: ai có `course:manage` quản lý
  được **mọi** course.
- [CourseReader.java](../../trellis-learning-content/src/main/java/io/trellis/learningcontent/domain/course/policy/CourseReader.java)
  mô hình hoá người đọc bằng `(userId, manager)` — hai bit. Không diễn đạt được "tôi là
  instructor của course **NÀY**".

**Câu hỏi của use case này:** *"User này có được quản lý course NÀY không?"*

### 1.3. Bốn defect trong base, tìm được khi đọc code

| # | Defect | Bằng chứng | Mức độ |
| --- | --- | --- | --- |
| D1 | **Role trong token leo thang thành permission.** `ClaimActorMapper` trộn `roles-claim` và `permissions-claim` vào **một** set phẳng `Actor.authorities()`. `ConfiguredPermissionResolver` rồi cấp thẳng bất kỳ authority *có hình dạng* `a:b` thành permission — không qua map role, không đối chiếu `PermissionRegistry`. Một realm role Keycloak đặt tên `course:manage` trở thành permission `course:manage`. | `ClaimActorMapper.toActor` + `ConfiguredPermissionResolver.permissionsOf` (regex `PERMISSION_SHAPE`) | Cao về thiết kế. Không phải user tự leo thang (tên role do admin IdP đặt), nhưng phá đúng ranh giới ADR 0002 muốn dựng, và đi vòng qua chính cái fail-fast mà `PermissionCatalogScanner` tồn tại để làm. |
| D2 | **`audiences` rỗng ở prod = nhận mọi audience.** `issuer-uri` bị bắt buộc ngoài dev/test (`JwtDecoders` throw), `audiences` thì không: `application-prod.yml:158` để `${TRELLIS_OIDC_AUDIENCES:}` và validator bị bỏ qua khi rỗng. Mọi token do cùng realm phát cho **bất kỳ** client nào đều được chấp nhận. | `application-prod.yml:157-158`, `JwtDecoders.build` | Cao ở prod. |
| D3 | **`VersionETags` copy nguyên văn** sang `trellis-notification` và `trellis-learning-content`, giống nhau từng byte trừ error code. Parse `If-Match` là luật protocol, không phải business; AGENTS.md đã xếp nó vào phần REST của platform. | hai file `VersionETags.java` | Trung bình, nhân lên mỗi module mới. |
| D4 | **Code chết + map hai lần.** `configuredPermissions()` không ai gọi. `ClaimActorMapper.toActor` chạy trong filter chain rồi chạy **lại** mỗi `currentActor()` — dựng `Actor` hai lần một request. | grep `configuredPermissions`; `SecurityFilterChains:73` vs `SecurityContextActorResolver` | Thấp. |

---

## 2. Lưu phân quyền ở đâu: bốn phương án

Đã tra thực tiễn ngành; nguồn ở mục 8. Lưu ý: phần lớn là blog thực hành và tài liệu vendor,
không phải chuẩn hay benchmark đo được — nên đọc theo hướng định hướng, không phải chân lý.

### S1. Bảng RBAC trong Postgres, có scope *(phương án được chọn)*

`permission` (seed từ code) · `role` · `role_permission` · `role_grant(subject, role, scope_type, scope_id)`.
Resolver đọc DB qua cache ngắn. List endpoint đẩy điều kiện grant xuống SQL.

- Đúng hình dạng mà các guide khuyên cho scope linh hoạt: một bảng assignment với
  `scope_type` + `scope_id` tuỳ chọn, để một dòng nhắm vào org, team hay project đều được.
- Phần khó nhất — list/search — thành một `EXISTS` trong **cùng** Postgres đang giữ resource.
- Là pattern hybrid mà hầu hết B2B SaaS rơi vào: RBAC thô ở mức org, phân quyền mức resource
  ở dưới.

### S2. Permission nằm trong token (Keycloak làm nguồn sự thật)

Dùng Keycloak Authorization Services / RPT / protocol mapper, backend chỉ đọc claim, không bảng.

**Loại.** Bốn lý do, theo thứ tự nặng dần:

1. **Không giải được list filtering.** Không JOIN được với một claim. Mọi endpoint danh sách
   sẽ phải lọc sau khi query — mà lọc sau trang thì page hụt hàng và cursor nhảy.
2. **Token bloat.** Scope theo từng course nghĩa là nhét id của mọi course mà user quản lý vào
   JWT. Không có giới hạn trên.
3. **Staleness.** Claim là snapshot lúc phát token; thu hồi quyền chỉ có hiệu lực khi token hết
   hạn. Các nguồn đều xếp "fine-grained entitlement" và "admin right" vào loại **không nên**
   nằm trong claim dài hạn, và nói rõ: mọi cách vá staleness (denylist, version counter) đều
   mang lookup server-side quay trở lại — đúng cái mà stateless định tránh.
4. **Phá ranh giới.** Keycloak sẽ phải biết permission của Trellis. Có báo cáo Keycloak chậm
   rõ rệt khi bật fine-grained authorization (~200ms so với ~16ms trên admin API), tuy đó là
   admin console chứ không phải runtime check.

### S3. Policy engine ngoài kiểu Zanzibar (OpenFGA / SpiceDB) hoặc PDP (Cerbos)

**Loại ở phase này** — nhưng S1 được thiết kế để không chặn đường sang đây.

- **Dual-write.** Postgres là nguồn sự thật của course; tuple store là nguồn sự thật của quan
  hệ. Hai nguồn, hai transaction. Lệch dữ liệu ở đây **là** lỗ hổng bảo mật.
- **List endpoint vẫn là chỗ đau.** Cách khuyến nghị là `LookupResources` rồi lọc, và chính tài
  liệu vendor giới hạn nó cho tập nhỏ: SpiceDB lấy ví dụ dưới 10.000 resource, một thread
  OpenFGA nói khoảng 1.000. Danh sách course của một tenant không nhỏ như thế. CNCF cũng chỉ ra:
  mỗi row bị loại phải nạp thêm một row để đủ page size.
- **Trọng lượng vận hành.** Thêm một service stateful + datastore riêng vào stack, cho **use
  case đầu tiên**.
- Đáng chú ý: ngay cả tài liệu SpiceDB cũng khuyên giữ **bản sao local** của quan hệ để
  list quay về một JOIN. Nếu lời giải cuối cùng là một JOIN trong Postgres, thì S1 là lời giải
  đó mà không cần service thứ hai.

### S4. Embedded policy library (Casbin Java) với adapter lưu policy trong DB

**Loại.** Policy ở DB, in-process, không round-trip — nghe khớp. Nhưng:

- Nó mang vào một **DSL thứ hai** (model CONF + policy rows) đứng cạnh `@PermissionCatalog` và
  `PermissionRequirement` đã có. Hai vocabulary cho một quyết định.
- Casbin **không có query planner**: tài liệu của chính nó nói list filtering cần một
  materialized table hoặc một thứ như `PlanResources` của Cerbos / `LookupResources` của
  Zanzibar. Tức là **vẫn phải tự làm đúng phần khó nhất**.
- Nguy cơ lệch semantics: policy evaluate ngoài SQL có thể nhận một row mà SQL sẽ từ chối,
  nhất là khi có phủ định.

Nhận một dependency và một DSL mà vẫn phải tự giải phần khó thì không đáng.

### Vì sao chọn S1

Phần khó của authorization trong ứng dụng này không phải "user X có permission P không" — chỗ
đó cái nào cũng làm được. Phần khó là **list và search**: trả về đúng trang, đúng số hàng,
cursor không nhảy, không rò rỉ tổng số. Mọi nguồn đều về cùng một kết luận: cách bền vững là
đẩy điều kiện xuống data store (OPA: *"write policy in OPA, enforce policy in SQL"*), còn
check từng row qua engine ngoài là phương án tệ nhất cho pagination.

Postgres đã là nguồn sự thật của resource. Đặt grant **cùng** trong Postgres biến bài toán khó
nhất thành một `EXISTS` trong cùng transaction: không dual-write, không service mới, không
giới hạn 1.000 phần tử.

---

## 3. Tách hai thứ hay bị gộp

Đây là mấu chốt trả lời cho câu "sao lại đi update config?":

| | Nguồn sự thật | Vì sao |
| --- | --- | --- |
| **Tập permission nào tồn tại** (`course:manage` là một permission hợp lệ) | **Code** — `@PermissionCatalog` | Permission là vocabulary của code: nó tương ứng một nhánh logic. Thêm permission mà không có code dùng nó thì vô nghĩa. Giữ ở code mới có fail-fast khi typo, ArchUnit, và grep ra được chỗ dùng. |
| **Ai có permission nào, trên resource nào** (`user u là instructor của course c`) | **Database** — `role`, `role_permission`, `role_grant` | Đây là dữ liệu vận hành: đổi mỗi ngày, do admin/tenant đổi, phải có lịch sử và audit, không được cần deploy. |

Cái đang sai hôm nay là nhóm thứ hai nằm trong YAML. Nhóm thứ nhất ở code là **đúng** và sẽ
giữ nguyên — bảng `permission` chỉ là bản chiếu của `PermissionRegistry`, được seed lúc
`setup`, tồn tại để DB có khoá ngoại mà chặn grant một permission không tồn tại.

**Một luật xuất phát từ cảnh báo trong nguồn:** đừng bao giờ để map hardcode và map DB nằm
trong **cùng** một đường resolve — đó là nơi sinh ra loại bug tệ nhất. Vì vậy YAML không còn là
fallback runtime; nó chỉ **ghi vào DB** ở profile `setup`, rồi im lặng. Runtime đọc đúng một
nguồn.

YAML vẫn phải tồn tại ở vai đó, vì bài toán con gà–quả trứng: một môi trường mới dựng lên phải
có sẵn một admin, nếu không thì không ai cấp quyền được cho ai.

---

## 4. Năm quyết định thiết kế chốt sẵn

### 4.1. Grant lưu ở `trellis-platform`, scope là chuỗi opaque

Platform sở hữu bảng grant và biết "scope" như **một cặp chuỗi vô nghĩa với nó**
(`scope_type`, `scope_id`); nó không bao giờ biết `course` là gì. Để mỗi business module tự
giữ bảng grant thì mỗi module phát minh lại cache, định dạng, ngữ nghĩa expiry, và
`AuthorizationService` mất vị thế "chỗ duy nhất quyết định".

### 4.2. Business repository **không** được biết tên bảng grant

List query phải đẩy điều kiện grant xuống SQL, nhưng repo của module không hardcode tên bảng
của platform. Platform publish một SQL fragment builder:

```java
// io.trellis.platform.security.authz.sql.GrantPredicate
public static Fragment scopedGrantExists(String resourceIdColumn, String permission, String scopeType);
public record Fragment(String sql, Map<String, Object> params) {}
```

### 4.3. `ResourceScope` là tham số **bắt buộc**, không có default

```java
public record ResourceScope(String type, String id) {
    public static ResourceScope global();            // ("*", "*")
    public static ResourceScope of(String type, String id);
}

// AuthorizationService — đúng hai method, không overload nào bỏ scope
AuthorizationDecision decide(RequestContext ctx, PermissionRequirement requirement, ResourceScope scope);
default void require(RequestContext ctx, PermissionRequirement requirement, ResourceScope scope);
```

Không có `decide(ctx, requirement)`. Một `default` delegate về global scope nghĩa là **quên
truyền scope vẫn compile và vẫn pass** — biến lỗ hổng phân quyền thành thứ im lặng. Giờ
`ResourceScope.global()` là lựa chọn tường minh, grep được. Giá phải trả: sửa hết call site,
việc cơ học, compiler chỉ hết.

### 4.4. `Actor` tách `roles()` khỏi `permissions()` — sửa D1 ở tầng kiểu

```java
public sealed interface Actor {
    Set<String> roles();            // từ roles-claim: chỉ là tên, phải map qua role_permission
    Set<Permission> permissions();  // từ permissions-claim: lọc qua PermissionRegistry
}
```

Giữ nguồn gốc claim trong **kiểu dữ liệu**, thay vì trộn phẳng rồi đoán lại bằng regex. Role
tên `course:manage` sẽ nằm trong `roles()`, không khớp role nào trong `role_permission`, và cấp
đúng **không gì cả**. Permission bị bỏ vì không khai trong catalog thì log warn — bỏ im lặng là
cách tốt nhất để không ai phát hiện cấu hình sai.

### 4.5. Role có `tenant_id`, định danh bằng id chứ không bằng tên

Theo khuyến nghị cho multi-tenant: role do tenant tạo luôn mang `tenant_id`, `UNIQUE(tenant_id,
name)`, và `tenant_id IS NULL` là role hệ thống. Nhờ vậy hai tenant đều có role tên "Manager"
với tập quyền khác nhau mà không phải migrate gì về sau. Trellis chưa có tenant thật, nhưng
**cột này rẻ bây giờ và đắt sau** — thêm `tenant_id` vào khoá của một bảng đã có dữ liệu là một
cuộc migration thật.

---

## 5. Các bước

### Bước 0 — ADR 0002: mô hình authorization  *(không code)*

`../adr/0002-authorization-model.md`, theo template `0001`. Chốt bằng văn bản: ranh giới IdP
↔ Trellis (Keycloak lo authentication + role thô, **không bao giờ** biết permission); tách hai
nguồn sự thật ở mục 3; grant là `(subject, role, scope)`; scope bắt buộc và vì sao; deny by
default; **403 vs 404** (không thấy được → 404, không rò rỉ sự tồn tại; thấy được mà không sửa
được → 403); `tenantId` chưa enforce và vì sao; loại khỏi scope: PAT, ABAC/OPA, user store
riêng — kèm lý do và kèm **điều kiện để xét lại S3** (mục 7).

Ghi cả bốn phương án ở mục 2 vào ADR: giá trị của ADR là lý do loại, không phải lựa chọn.

### Bước 1 — sửa base  *(breaking, làm trước khi chồng thêm gì)*

| Việc | Chi tiết |
| --- | --- |
| **D1** | `Actor` tách `roles()`/`permissions()` (4.4). `ClaimActorMapper` đọc hai claim thành hai set. Xoá regex `PERMISSION_SHAPE`. Permission từ token lọc qua `PermissionRegistry`, cái bị bỏ log warn. |
| **D2** | `SecurityProperties.hasAudience()`; `JwtDecoders.create` throw ngoài dev/test khi `audiences` rỗng, **cùng cơ chế** với `hasKeySource()`. Xoá default rỗng ở `application-prod.yml`. |
| **D3** | `VersionETags` lên `io.trellis.platform.web.rest`, nhận `ErrorCode` làm tham số. Xoá hai bản copy. Bỏ nó khỏi phần "copy những file này" của `new-module.sh` và `../../trellis-notification/README.md`. |
| **D4** | Xoá `configuredPermissions()`. `Actor` dựng **một lần** mỗi request: đặt lên `Authentication`, `SecurityContextActorResolver` đọc ra thay vì map lại. |

Chưa thêm tính năng nào. Mỗi defect là một dòng trong `../how/breaking-changes.md`, viết cho
người không đọc code.

### Bước 2 — schema và resolver

`trellis-platform/src/main/resources/db/changelog/platform/security/001-access.sql`:

```sql
--changeset trellis:security-001-permission
--comment: Mirror of the @PermissionCatalog registry, seeded by the setup profile. Code owns which
--comment: permissions exist; this table exists so the database can reject grants of unknown ones.
CREATE TABLE permission (
    name        VARCHAR(150) PRIMARY KEY,
    description VARCHAR(500)
);

--changeset trellis:security-002-role
--comment: tenant_id NULL is a system role. Identity is the id, never the name, so two tenants may
--comment: both have a "Manager" with different grants.
CREATE TABLE role (
    id         UUID         PRIMARY KEY,
    tenant_id  VARCHAR(100),
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_role_name UNIQUE NULLS NOT DISTINCT (tenant_id, name)
);

--changeset trellis:security-003-role-permission
--comment: One source of truth for "what can this role do", for token roles and DB grants alike.
CREATE TABLE role_permission (
    role_id    UUID         NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    permission VARCHAR(150) NOT NULL REFERENCES permission (name),
    PRIMARY KEY (role_id, permission)
);

--changeset trellis:security-004-role-grant
--comment: Who holds which role, over which scope. scope_type '*' is a global grant.
CREATE TABLE role_grant (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subject_type VARCHAR(16)  NOT NULL,
    subject_id   VARCHAR(200) NOT NULL,
    role_id      UUID         NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    scope_type   VARCHAR(60)  NOT NULL,
    scope_id     VARCHAR(200) NOT NULL,
    granted_at   TIMESTAMPTZ  NOT NULL,
    granted_by   VARCHAR(200) NOT NULL,
    expires_at   TIMESTAMPTZ,
    CONSTRAINT uq_role_grant UNIQUE (subject_type, subject_id, role_id, scope_type, scope_id),
    CONSTRAINT ck_role_grant_subject_type CHECK (subject_type IN ('USER', 'SERVICE'))
);
CREATE INDEX ix_role_grant_subject ON role_grant (subject_id, scope_type, scope_id)
    WHERE expires_at IS NULL;
CREATE INDEX ix_role_grant_scope ON role_grant (scope_type, scope_id);
```

Khoá ngoại `role_permission.permission → permission(name)` thay cho một startup check: DB
không cho tồn tại một grant tới permission không khai trong catalog. Đó là chỗ kiểm tra đúng —
nó đúng cả khi ai đó `INSERT` tay.

Code mới trong `io.trellis.platform.security.authz`:

| Class | Việc |
| --- | --- |
| `RoleGrantStore` (port) + `JdbcRoleGrantStore` | đọc/ghi grant qua `JdbcClient`, SQL native trong text block |
| `DbPermissionResolver implements PermissionResolver` | hợp của: permission từ role được grant trong DB cho scope đang hỏi **và** cho scope `*`; permission từ `roles()` của token map qua **cùng** `role_permission`; `permissions()` đã lọc registry |
| `GrantCache` (Caffeine) | key `(subjectId, tenantId, scope)` — theo khuyến nghị cache key phải gồm tenant và membership; TTL ngắn, max size; invalidate khi ghi grant |
| `PermissionTableSeeder` (`MigrationStep`) | đồng bộ `permission` từ `PermissionRegistry`; xoá dòng không còn trong catalog |
| `BootstrapGrantSeeder` (`MigrationStep`) | nạp `trellis.security.bootstrap.role-permissions` vào `role`/`role_permission` nếu chưa có; idempotent |

**Điểm tinh tế phải làm đúng:** role từ token và grant trong DB **cùng** đi qua
`role_permission`. Hai đường map khác nhau = hai câu trả lời cho "role này làm được gì", và sẽ
lệch.

`ConfiguredPermissionResolver` **xoá khỏi đường chạy runtime**. Key YAML đổi tên thành
`trellis.security.bootstrap.role-permissions` để vai mới hiện ra trong chính cái tên.

Config mới (mỗi key cần comment trong `application.yml` dạng `${TRELLIS_*:default}` **và** một
dòng phân loại trong `SensitiveProperties`, nếu không build fail):

```yaml
trellis.security.grants:
  cache-ttl: ${TRELLIS_GRANT_CACHE_TTL:PT30S}
  cache-max-size: ${TRELLIS_GRANT_CACHE_MAX_SIZE:10000}
```

TTL ngắn là lựa chọn tường minh: thu hồi quyền có hiệu lực trong vòng `cache-ttl`, không phải
chờ token hết hạn. Đây chính là thứ S2 không làm được.

### Bước 3 — scope trong mô hình authorization  *(trái tim của use case)*

`trellis-common`: `ResourceScope`, `AuthorizationService`, `PermissionResolver` theo 4.3.
Compiler chỉ ra mọi call site cần sửa — đi hết, không để `global()` làm chỗ trốn.

`DefaultAuthorizationService` **giữ `final`** và giữ nguyên hình dạng `switch` (system allow,
anonymous deny), chỉ truyền scope xuống resolver. Metric `trellis.authz.decisions` thêm tag
`scope_type`; **không** thêm `scope_id` — cardinality explosion trong Prometheus.

`trellis-learning-content`:

```java
public Course requireManageable(RequestContext ctx, Course course) {
    authorization.require(ctx, MANAGE, ResourceScope.of(CourseScopes.COURSE, course.id().toString()));
    return course;
}
```

`CourseCommands` gọi nó **sau khi load** aggregate — đúng luật T8: phân quyền phụ thuộc dữ liệu
là call tường minh, **không** phải annotation. `@RequiresPermission` ở lại cho operation chỉ
phụ thuộc "ai gọi" (ví dụ `create`). `CourseVisibility` (domain) không đổi: vẫn là hàm thuần
trên `CourseReader`; scope được resolve ở application layer trước khi vào domain.

### Bước 4 — đẩy filter xuống SQL

`CourseFilter` thêm `boolean scopedManage`; `JdbcCourseRepository.page` ghép fragment từ
`GrantPredicate` (4.2):

```java
private static final String VISIBLE_SCOPED =
        " AND (status IN (:visibleStatuses) OR owner_id = :ownerId OR %s) ";
```

**Không post-filter sau khi query.** Lọc sau trang làm page hụt hàng, keyset cursor nhảy, và
tổng số rò rỉ số course caller không được thấy — đúng thất bại mà cả OPA và CNCF mô tả. Comment
trong [CourseFilter.java](../../trellis-learning-content/src/main/java/io/trellis/learningcontent/application/course/port/CourseFilter.java)
đã hứa điều này; bước này giữ lời hứa khi thêm grant.

Figure 18 (`../figures/figure-18-search-indexing-and-authorization.png`) nói PostgreSQL là
nguồn authoritative cho visibility — bước này chính là nó.

### Bước 5 — bề mặt vận hành

Phân quyền trong DB chỉ có nghĩa khi quản trị được **không cần deploy**. Ba endpoint, đặt ở
**`trellis-app`** cùng chỗ các endpoint ops hiện có (platform là library, không tự mở endpoint):

- `GET|POST|DELETE /api/platform/access/grants` — cấp/thu hồi grant, sau `platform.access:manage`,
  `@Audited`, `Idempotency-Key` trên POST, keyset page.
- `GET|POST /api/platform/access/roles` — tạo role và gán permission cho role.
- `GET /api/platform/audit-events` — lọc theo `aggregateType`/`aggregateId`/`actorId`/khoảng
  thời gian, sau `platform.audit:read`. Bảng `audit_event` đã có index
  `(aggregate_type, aggregate_id, id)` và `(occurred_at)`
  ([001-audit-event.sql](../../trellis-platform/src/main/resources/db/changelog/platform/aop/001-audit-event.sql)) —
  đọc được ngay, **không cần migration**.

Sau bước này: `./gradlew :trellis-app:exportApiContracts`, rồi `cd web && pnpm codegen` và
`uv run python ../contracts/codegen/python/generate_sdk.py`, commit tất cả — `checkApiContracts`
chạy trong `check` nên bỏ sót là CI đỏ.

### Bước 6 — codify (skills, rules, guardrails)

**ArchUnit** (thêm vào `../../trellis-app/src/test/java/io/trellis/app/architecture/ArchitectureTest.java`,
cạnh `businessWebStaysThin`):

| Rule mới | Chặn điều gì |
| --- | --- |
| `permissionStringsOnlyInCatalogs` | permission string literal ngoài class `@PermissionCatalog` |
| `scopeTypeStringsOnlyInScopeCatalogs` | scope type literal rời rạc (`"course"`) rải trong code |
| `webDoesNotAuthorize` | `web/**` gọi `AuthorizationService` — AGENTS.md đã bắt, chưa có rule nào enforce |
| `scopedDecisionsLoadTheAggregateFirst` | `require(..., ResourceScope.of(...))` trong class không có repository dependency — dấu hiệu scope bị bịa từ input thay vì từ row đã load |

**Skill** `.agent-skills/security/` — `SKILL.md` + `standards/authorization.md`, theo luật của
[.agent-skills/README.md](../../.agent-skills/README.md): dưới ~120 dòng, link chứ không restate,
**chỉ chép luật đã tồn tại** ở ADR 0002.

**Docs:** mục "Authorization" trong `../../AGENTS.md`; cập nhật `docs/architecture/backend.md` §11/§12;
`docs/how/breaking-changes.md` cho D1–D4 và cho việc `role-permissions` đổi vai.

---

## 6. Hoãn có chủ ý

| Thứ | Vì sao hoãn |
| --- | --- |
| PAT / API key | Chưa ai cần: BFF dùng token Keycloak, worker dùng client-credentials. |
| Multi-tenancy enforcement | Chưa có tenant thật. Cột `tenant_id` có sẵn trong schema, nhưng chưa enforce; ADR 0002 nói rõ. |
| Role phân cấp (role kế thừa role) | Thêm được sau bằng một bảng `role_parent` mà không phá gì. Chưa có nhu cầu thật. |
| Quan hệ phân cấp org → course → lesson | Là lúc nên xét lại S3 (mục 7). |
| User store riêng trong Trellis | Keycloak lo. Trellis chỉ giữ `subject_id`. |
| Enrolment-driven read access | Use case **thứ hai**, trên module enrolment chưa tồn tại. |

---

## 7. Khi nào xét lại S3 (policy engine ngoài)

Ghi vào ADR 0002 để quyết định sau này có tiêu chí, không phải tranh luận lại từ đầu. Xét lại
khi **bất kỳ** điều nào xảy ra:

- Quyền bắt đầu **kế thừa qua nhiều bậc** (quyền trên org tự động cho quyền trên mọi course của
  nó, rồi mọi lesson của course) — đúng chỗ ReBAC thắng, và chỗ `EXISTS` phẳng bắt đầu thành
  recursive CTE khó đọc.
- Cần trả lời **"ai có quyền trên resource X"** (reverse lookup) cho UI chia sẻ, không chỉ
  "user này có quyền không".
- Có service thứ hai ngoài Java cần ra **cùng** quyết định authorization — lúc đó một PDP dùng
  chung rẻ hơn hai bản implement.

Thiết kế S1 không chặn đường: `PermissionResolver` là SPI một method, `GrantPredicate` là chỗ
duy nhất biết hình dạng SQL. Đổi sang OpenFGA/SpiceDB là thay hai class đó, không phải sửa
business code.

---

## 8. Nguồn

Lưu trữ phân quyền, config vs DB:
- [Stop Hardcoding Roles: A Practical Guide to Roles, Permissions, and Scalable Authorization](https://dev.to/d_lynol/stop-hardcoding-roles-a-practical-guide-to-roles-permissions-and-scalable-authorization-1cf) — cảnh báo đừng để map hardcode và map DB trong cùng đường resolve
- [How to Build a Role-Based Access Control Layer (Oso)](https://www.osohq.com/learn/rbac-role-based-access-control)
- [Best Practices for Managing Permissions in a JWT-Based Auth Setup](https://github.com/better-auth/better-auth/discussions/3314)
- [Role-based access control (Wikipedia)](https://en.wikipedia.org/wiki/Role-based_access_control)

Schema cho scoped RBAC / multi-tenant:
- [Role-Based Access Control: Design and Implementation](https://www.jamesrossjr.com/blog/role-based-access-control-guide) — `scope_type` + `scope_id` trên bảng assignment
- [How to design an RBAC model for multi-tenant SaaS (WorkOS)](https://workos.com/blog/how-to-design-multi-tenant-rbac-saas) — `UNIQUE(tenant_id, name)`
- [Enterprise RBAC System Design](https://www.nirajkumar.in/blog/designing-enterprise-rbac-system) — role định danh bằng id, cache key gồm tenant
- [Roles and Permissions Schema Design for Multi-Tenant RBAC](https://dev.to/tbson87/roles-and-permissions-schema-design-for-multi-tenant-rbac-part-2-4gc0) — composite FK chặn cross-tenant
- [RBAC Best Practices for Multi-Tenant SaaS (Frontegg)](https://frontegg.com/guides/role-based-access-control-best-practices) — search/count/export/pagination rò rỉ dữ liệu

Mô hình RBAC vs ABAC vs ReBAC:
- [RBAC vs ABAC vs ReBAC vs PBAC](https://guptadeepak.com/guides/rbac-abac-rebac-pbac/) — pattern hybrid
- [Google Zanzibar Explained](https://guptadeepak.com/ciam-compass/guides/zanzibar-explained/)
- [RBAC vs ABAC vs ReBAC: OpenFGA Wins at Scale](https://shattered.io/rbac-vs-abac-vs-rebac-opa-cedar-openfga-spicedb-2026/)
- [OpenFGA vs SpiceDB vs Cerbos vs OPA](https://sph.sh/en/posts/external-authorization-management-systems/)

List/search authorization — nguồn quyết định cho Bước 4:
- [Protecting a List Endpoint (Authzed/SpiceDB)](https://authzed.com/docs/spicedb/modeling/protecting-a-list-endpoint) — giới hạn của `LookupResources`, và khuyến nghị giữ bản sao local để JOIN
- [Write Policy in OPA. Enforce Policy in SQL.](https://www.openpolicyagent.org/blog/write-policy-in-opa-enforce-policy-in-sql-d9d24db93bf4) — per-resource query phá pagination
- [The technical complexities of externalized authorization (CNCF)](https://www.cncf.io/blog/2024/11/18/the-technical-complexities-of-externalized-authorization/) — loại một row phải nạp thêm row để đủ page
- [List Filtering (Casbin)](https://casbin.apache.org/docs/list-filtering/) — Casbin không có query planner
- [OpenFGA authorization integration (Apicurio #10657)](https://github.com/Apicurio/apicurio-registry/issues/10657) — ngưỡng ~1.000 object

Permission trong token:
- [The Case Against Token-Based Authorization (Cerbos)](https://www.cerbos.dev/blog/the-case-against-token-based-authorization)
- [Should organisations put roles and permissions inside JWT claims?](https://nhimg.org/faq/should-organisations-put-roles-and-permissions-inside-jwt-claims/)
- [You Changed a User's Role in the Database, But Their JWT Doesn't Know That Yet](https://dev.to/anas_sheikh_2/you-changed-a-users-role-in-the-database-but-their-jwt-doesnt-know-that-yet-4g6k)
- [Keycloak Authorization Services Guide](https://www.keycloak.org/docs/latest/authorization_services/index.html)
- [You Have Keycloak. What Authorization Capabilities Do You Still Need? (Cerbos)](https://www.cerbos.dev/blog/you-have-keycloak-what-authorization-capabilities-do-you-still-need) — vendor, đọc có bộ lọc
- [Admin API extremely slow with fine-grained authorization (keycloak#31519)](https://github.com/keycloak/keycloak/issues/31519)

Thư viện embedded:
- [Authorization in 2025: A Practical Comparison](https://medium.com/@giorgioprof/authorization-in-2025-a-practical-comparison-of-modern-solutions-a55fe9bf8069)
- [Authorization libraries — Casbin and OpenFGA](https://www.securecoding.com/application-security/authorization-libraries/)

---

## 9. Điểm cần chủ repo quyết

**9.1. Test.** T5 nói không viết feature test. Nhưng authorization là chỗ test *kiếm được chỗ
đứng*: regression ở đây là lỗ hổng bảo mật, không phải bug hiển thị, và nó im lặng.

Đề nghị **một ngoại lệ hẹp**: `CourseAccessIT` theo khuôn `NotificationFlowTest` (token RS256 thật qua
`@TestBean JwtDecoder`, `PostgresTestcontainer`), phủ đúng năm thứ:

1. instructor có grant trên course A sửa được A;
2. cùng instructor đó nhận **404** (không phải 403) với course B mình không thấy;
3. nhận **403** với course mình thấy được nhưng không được sửa;
4. list không rò rỉ: trang trả về đúng số hàng, cursor không nhảy;
5. role tên `course:manage` trong `roles-claim` cấp **không gì cả** (D1).

Nếu không được duyệt: làm Bước 3–4 không test, ghi rủi ro thành một mục trong ADR 0002. Không
làm lặng lẽ.

**9.2. Xác nhận breaking.** Bước 1 và 2 đổi hành vi của base: token cấu hình kiểu cũ có thể mất
permission (D1), app thiếu `TRELLIS_OIDC_AUDIENCES` **không khởi động được** ở prod (D2), key
YAML đổi tên và đổi vai. Với source base chưa có product thì đây là đúng thời điểm; cần bạn xác
nhận không có môi trường nào đang chạy mà việc này làm vỡ.

---

## 10. Thứ tự merge

```
Bước 0  ADR 0002                        (docs, merge trước tiên)
Bước 1  sửa base D1–D4                  (breaking; chưa thêm tính năng nào)
Bước 2  schema + DbPermissionResolver   (cần Bước 1)
Bước 3  ResourceScope + CourseAccess    (use case; cần Bước 2)
Bước 4  SQL pushdown                    (cần Bước 3)
Bước 5  access + audit API              (độc lập với 3–4, cần Bước 2)
Bước 6  ArchUnit + skill + docs         (merge cuối, khi luật đã đúng với code)
```

Bước 2 cố ý seed đúng bằng `role-permissions` hiện có trong `application-dev.yml`, nên nếu nó
làm vỡ gì thì vỡ ngay, trước khi có logic scope chồng lên.
