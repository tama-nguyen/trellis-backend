# Kế hoạch khắc phục security và identity

Trạng thái đã triển khai và gom commit theo phases. Deployment prerequisites được ghi tại
[security remediation guide](../guide/security-remediation.md). Ngày 2026-11-24.

Baseline `0ab180b6ffcf2d2746f455f02f9142217a47d266`, branch `feat/user-permission`.
Các findings từ nghiên cứu ban đầu được đối chiếu theo mã R trong bảng phía dưới.
Thư mục báo cáo `docs/security-review/` đã được loại khỏi working tree khi gom lịch sử;
snapshot trước khi gom được giữ trong backup ngoài repository.

## 1. Phạm vi và thứ tự

| Nhóm | Finding | Mục tiêu |
| --- | --- | --- |
| P0 | R1 | Native access token hoạt động với schema PostgreSQL thực |
| P1 | R10 | JWT có issuer được kiểm tra cùng chữ ký, thời gian và audience |
| P1 trước multi-tenancy | R5 | Token role chỉ resolve system role khi chưa có tenant context |
| P1 | R4 | Tách identity native/OIDC/service trong profile, grant và ownership |
| P1 cần chốt policy | R2 | Quyền tạo course được khai báo rõ và test theo policy đã chọn |
| P1 | R3, R11 | Detail/list/update/activate/archive áp dụng cùng semantics scoped permission |
| P1 | R6 | Stored grant và role mapping được thu hồi theo commit database |
| P2 review design | R8 | Gửi mail có retry bền vững, tách khỏi business transaction |
| P2 review design | R9 | Audit security success/failure theo đúng transaction semantics |
| P2 kiểm thử concurrency | R12 | Serialize issuance và consumption action token đúng thứ tự lock |

R1 triển khai trước. R10 cung cấp issuer đáng tin cậy cho R4. R5 giới hạn rõ phạm vi system roles
trong khi tenant model chưa được triển khai. R2/R3/R11 đi cùng một policy matrix trước khi sửa code.
R12 chốt protocol token trước khi R8 đưa issuance/delivery sang worker bất đồng bộ.
R9 có thể triển khai sau các thay đổi P1 khi identity key và outcome taxonomy đã ổn định.

Các đề xuất policy, namespace, caching và mail delivery dưới đây cần được review như quyết định
kiến trúc trước phase tương ứng. Việc tạo plan này chưa đồng nghĩa đã chấp thuận các thay đổi hành vi.

## 2. Quy tắc triển khai và JUnit

- Dùng JDK 21, JUnit/Mockito theo dependency đang pin trong repo, Google Java Format.
- Test class dùng hậu tố `Test`, đặt tên theo lớp hoặc capability thực sự được kiểm tra.
  Mỗi production class có unit test riêng khi cần. Các kịch bản HTTP/concurrency có class riêng.
- Unit test dùng MockitoExtension, mock các port và dùng domain values thật. Spring integration test
  dùng proxy thật để kiểm tra aspect, authorization và transaction.
- Persistence/security integration dùng PostgreSQL Testcontainers, schema Liquibase thật và
  repository thật. Mock UserRepository không đủ để kiểm chứng SQL hoặc token revocation.
- JWT test tạo keypair trong test runtime. Dùng local JWKS HTTP stub và token được ký thật cho các
  trường hợp signature/issuer/audience. Test HTTP lỗi hạ tầng tách khỏi token sai.
- Concurrency test dùng hai connection/transaction độc lập, latch/barrier và timeout hữu hạn.
  Đặt checkpoint để kiểm tra thứ tự, tránh phụ thuộc `Thread.sleep`.
- `@RateLimited` thuộc public controller methods. Use case giữ business policy, authorization và
  transaction. Test phân biệt quota HTTP với quyền nghiệp vụ.
- Giữ Spring field `@Value` theo convention hiện tại. Cấu hình chung thuộc `resources/config/`,
  cấu hình môi trường thuộc profile. Classify key mới trong SensitiveProperties.
- Mỗi fix đi cùng regression test. Test phải chứng minh hiệu ứng dữ liệu hoặc quyết định truy cập.
- Schema thay đổi bằng changeset mới. Contracts được regenerate bằng task repo khi thay đổi wire
  behavior. Breaking changes có migration và rollback notes.

## 3. Phase 1 — R1, sửa native access lookup

### Thay đổi

Sửa `UserJpaRepository.findByActiveAccessToken` join `users`. Đối chiếu entity mapping với schema.
Giữ truy vấn lọc access hash, revoked_at và access_expires_at, đồng thời giữ provider yêu cầu ACTIVE.

Điểm sửa chính nằm ở
[UserJpaRepository](../../trellis-identity/src/main/java/io/trellis/identity/infrastructure/persistence/user/UserJpaRepository.java),
[NativeTokenAuthenticationProvider](../../trellis-identity/src/main/java/io/trellis/identity/infrastructure/security/NativeTokenAuthenticationProvider.java)
và schema identity. Evidence R1 nằm tại E07/E23 trong source index.

### JUnit regression

| Class dự kiến | Kịch bản và assertion |
| --- | --- |
| `UserAccessTokenRepositoryTest` | Schema thật, user/session thật. Hash hợp lệ tìm đúng user. Sai hash, expired, revoked không tìm thấy. Mốc expiry bằng now bị từ chối |
| `NativeBearerAuthenticationTest` | Register/verify/login qua HTTP, dùng token nhận được gọi `/api/auth/me`. Xác nhận ID/email đúng. Password/hash/refresh secret không xuất hiện trong profile |
| `NativeBearerAuthenticationTest` | Missing/invalid bearer bị 401. User pending/disabled không được provider chấp nhận. Các token khác family không được coi là native access |
| `NativeSessionRevocationTest` | Refresh làm access cũ hết hiệu lực. Logout làm access của session đó hết hiệu lực. Reset làm toàn bộ session cũ hết hiệu lực |

Kiểm tra provider chưa mặc nhiên áp dụng mã 403 của SignInGuard cho request bearer.
HTTP assertion phải theo SecurityProblemMapper và authentication exception thực tế.

### Hoàn thành

Luồng login → protected HTTP request dùng repository thật pass. Các trường hợp revoked/expired bị
chặn. Fix này không cần thay schema hoặc định dạng token.

## 4. Phase 2 — R10 và R5, chốt issuer và system-role resolution

### R10 — Issuer

Đề xuất yêu cầu issuer-uri khi bật xác thực OIDC. jwk-set-uri tiếp tục có thể chỉ định vị trí lấy key,
trong khi issuer-uri xác định issuer được tin cậy. Mỗi JWT được chấp nhận phải thỏa issuer đã chọn,
chữ ký, thời gian và audience theo environment policy.

Dev/test có thể chạy native-only khi không cấu hình nguồn JWT key, với decoder từ chối mọi JWT
như hiện tại. Cấu hình JWKS mà thiếu issuer cần báo cấu hình sai ngay startup. Prod tiếp tục yêu cầu
audience. Review các giá trị Compose và profile, giữ trường hợp internal JWKS URL khác public issuer.

| Class dự kiến | Kịch bản và assertion |
| --- | --- |
| `JwtDecodersTest` | Cùng signing key và audience nhưng sai hoặc thiếu iss đều bị reject. Đúng issuer được accept. Sai audience, expired, nbf tương lai và sai signature bị reject |
| `JwtSecurityConfigurationTest` | JWKS-only không issuer fail startup. Native-only dev/test vẫn boot và từ chối JWT. Prod thiếu issuer/audience fail startup |
| `JwtAuthenticationHttpTest` | Token sai trả 401. Local JWKS endpoint không truy cập được trả 503 theo error mapping; retry header tồn tại |

Điểm sửa `JwtDecoders`, `SecurityProperties`, shared security YAML và environment overrides.
Evidence E04/E73/E74/E78.

### R5 — Tenant predicate

Chưa có tenant membership/context đáng tin cậy. Đề xuất giới hạn `permissionsForRoles` vào
`role.tenant_id IS NULL`, đồng nhất với `roleIdByName` của grant API. Ghi rõ tenant role chưa được
hỗ trợ ở runtime. Không lấy tenant ID trực tiếp từ header hoặc một claim chưa có trust contract.

Đối chiếu cả subject grant resolution và GrantPredicate. Quyết định phase này là chỉ system roles
được sử dụng, nên các query liên quan phải lọc role tương ứng; grant ghi trực tiếp bằng SQL tới
role có tenant phải không cấp quyền qua đường subject/list. Kiểm kê dữ liệu có sẵn trước rollout.

| Class dự kiến | Kịch bản và assertion |
| --- | --- |
| `RolePermissionResolutionTest` | Hai role cùng tên, system/tenant có permission khác nhau. Token role chỉ nhận system permissions. Tenant-only name không grant quyền |
| `ScopedGrantResolutionTest` | Grant tham chiếu tenant role được tạo bằng fixture SQL không cấp quyền qua single-row hoặc list predicate |
| `RoleGrantManagementTest` | Grant API chỉ resolve predefined system role; tenant role hoặc role không tồn tại trả lỗi, không tạo mapping/grant mới |

Tenant-aware support tương lai cần ADR và end-to-end tenant boundary riêng. P1 này chỉ chặn nhập
nhằng tên role trong trạng thái hiện tại. Evidence E24/E28/E43.

## 5. Phase 3 — R4, identity provenance và namespace

### Quyết định đề xuất

Mang provenance đã xác thực từ authentication qua Actor/RequestContext. Phân biệt NATIVE, OIDC user
và OIDC service bằng kiểu rõ ràng. Với OIDC, identity gồm issuer đã validate cùng subject hoặc client ID.
`GetCurrentUserUseCase` chỉ lookup native user khi provenance là NATIVE.

Đề xuất native principal ID giữ UUID hiện tại. External principal ID là opaque key có prefix loại
identity và digest xác định từ issuer + subject/client ID, với encoding không nhập nhằng và độ dài
phù hợp các cột subject_id/owner_id. Raw issuer/subject vẫn có thể được giữ trong authenticated
identity metadata để trace nguồn. Không suy provenance từ hình thức UUID hoặc prefix do client gửi.

Review các caller tạo Actor từ system jobs/events cùng lúc để internal actor không bị coi là external
identity đã xác thực. Chốt contract này bằng ADR trước khi sửa shared Actor API.

### Migration và tương thích

- Inventory role_grant.subject_id, course.owner_id, notice author/publisher và idempotency scope đang
  dùng external actor ID. Resource scope ID như course UUID giữ semantics của resource.
- Legacy external subject ID thiếu issuer không thể tự suy nguồn. Chuẩn bị mapping có nguồn đáng
  tin cậy; gặp ambiguity thì dừng mapping và yêu cầu đối chiếu dữ liệu, tránh tự gán sang native user.
- Backfill ownership/grants theo mapping đã duyệt. Không đổi actor ID trong lịch sử audit.
- Rollout đồng bộ identity contract giữa replicas và event adapters. Kiểm tra idempotency/replay
  behavior khi principal key đổi. Ghi breaking change của `/me` và client cache nếu external ID đổi.
- Rollback phải có mapping đảo và giới hạn thời điểm an toàn. Không bật song song old/new namespace
  khi việc này có thể tạo hai tập quyền cho cùng chủ thể.

### JUnit regression

| Class dự kiến | Kịch bản và assertion |
| --- | --- |
| `JwtActorMapperTest` | Cùng sub ở hai issuer tạo principal khác nhau. Cùng client ID ở hai issuer cũng khác. User/service cùng raw ID khác namespace |
| `GetCurrentUserUseCaseTest` | OIDC sub đúng bằng UUID native user vẫn dùng claim profile, không gọi native lookup. Native actor vẫn lookup theo UUID |
| `IdentityNamespaceAuthorizationTest` | OIDC actor không nhận native grant hoặc ownership chỉ vì raw sub trùng. Hai issuer không chia sẻ quyền, cache key hoặc owner equality |
| `IdentityNamespaceMigrationTest` | Mapping unambiguous được migrate đúng và lặp lại an toàn. Mapping ambiguous bị từ chối. Audit history giữ nguyên. Native IDs giữ nguyên |

Evidence E05/E06/E09/E21/E28/E29/E38/E68. Test phải đi qua token authentication thật cho ít nhất một
kịch bản collision, bổ sung unit tests cho mapping và lookup.

## 6. Phase 4 — R2/R3/R11, course authorization policy

### Policy đề xuất để chốt

| Operation | Đề xuất quyền | Resource visibility |
| --- | --- | --- |
| Create draft | Global `course:manage` | Tạo course chưa có UUID để cấp scoped grant trước đó |
| Detail | Global hoặc exact-scope READ/MANAGE | MANAGE xem mọi trạng thái. READ xem ACTIVE hoặc course của chính actor |
| List | Cùng policy với detail | Mỗi row phải thỏa đúng predicate detail trước keyset pagination |
| Update/activate/archive | Global hoặc exact-scope MANAGE | Ownership riêng lẻ không cấp quyền mutation |

R2 cần owner chốt. Nếu sản phẩm cần instructor tự tạo course, định nghĩa capability tạo riêng và
role mapping tương ứng thay vì suy instructor từ owner_id. Plan mặc định đề xuất global MANAGE.
Chỉ bắt đầu sửa phase này sau khi policy create được chọn.

### Thay đổi

- R2 thêm guard create theo policy đã chọn, trước persistence và audit.
- R3 bỏ global-only MANAGE guard thừa ở update khi đã dùng resource-aware manageable loader.
  Kiểm tra update/activate/archive dùng cùng resource scope và quyền.
- R11 mở rộng SQL list predicate cho scoped READ với đúng status/owner condition. Scoped MANAGE
  tiếp tục xem mọi trạng thái. Hợp nhất semantics qua helper/representation hiện có khi phù hợp,
  giữ SQL lọc trước pagination.
- So sánh detail và list bằng actor matrix gồm anonymous, user, service, owner, non-owner,
  global reader/manager, scoped reader/manager và expired/revoked grant.
- REST và GraphQL queries phải dùng cùng use cases/policy. Ghi rõ 401, 403, 404 và thứ tự visibility.

### JUnit regression

| Class dự kiến | Kịch bản và assertion |
| --- | --- |
| `CreateCourseAuthorizationTest` | Theo policy đã chốt, reader không tạo được course, manager tạo được. Denied request không insert course/audit |
| `CourseManagementAuthorizationTest` | Scoped manager của A update/activate/archive A thành công, B bị chặn. Owner không có MANAGE bị chặn. Global manager quản lý cả hai |
| `CourseVisibilityTest` | Unit matrix cho READ/MANAGE, ACTIVE/DRAFT/ARCHIVED, owner/non-owner và actor type |
| `CourseReadAuthorizationTest` | Với mỗi fixture row, row xuất hiện trong list khi và chỉ khi detail của actor được phép. Bao gồm scoped READ-only, scoped MANAGE, status, owner và expiry |
| `CoursePaginationAuthorizationTest` | Unauthorized rows không làm thiếu page; after/first, status và search filters vẫn hoạt động. Fixture đủ số row để vượt một page |
| `CourseGraphQlAuthorizationTest` | Cùng actor/course cho kết quả quyền tương đương REST. Hidden row có NOT_FOUND, list không chứa row ẩn |

Dùng repository PostgreSQL thật cho list và HTTP authenticated path cho mutation. Tests scoped-only
không được vô tình cấp global role permission trong fixture. Evidence E37–E47/E94–E96.

## 7. Phase 5 — R6, semantics thu hồi quyền

### Quyết định đề xuất cho P1

Dùng database làm nguồn hiện hành cho role mappings và stored grants ở mỗi authorization decision.
Bỏ cache permission liên request ở đường authorization P1 để loại bỏ TTL revocation delay và race
invalidate-before-commit. Review query count và index bằng traffic/load sau khi có correctness tests.
Nếu cần cache lại, mở design riêng với version/invalidation contract đã kiểm thử multi-instance.

Cam kết của phase này áp dụng cho decision bắt đầu sau commit grant/mapping change, đọc primary DB.
Request đã được phép trước revocation có thể hoàn tất. Direct permissions và role claims trong JWT
là nguồn quyền độc lập; revoke một stored grant không thể xóa quyền trùng được mang bởi JWT.
JWT permission revocation tức thời cần policy/token lifecycle riêng, chưa được suy từ grant API.

### Thay đổi và JUnit

| Class dự kiến | Kịch bản và assertion |
| --- | --- |
| `GrantRevocationConsistencyTest` | Hai application contexts/resolvers dùng cùng PostgreSQL. Warm access trên cả hai, commit revoke, các decision mới đều deny |
| `GrantRevocationTransactionTest` | Transaction revoke chưa commit không được coi đã thu hồi ở connection khác. Rollback giữ quyền; commit làm decision mới deny |
| `GrantExpiryAuthorizationTest` | Controlled Clock vượt expiresAt làm deny ở decision mới mà không chờ cache TTL |
| `RoleMappingChangeAuthorizationTest` | Xóa role_permission trong transaction fixture rồi commit, token role vẫn giữ tên nhưng không còn cấp permission đã bỏ |
| `PermissionSourceUnionTest` | Stored grant revoke chỉ loại nguồn stored grant. Direct JWT permission hoặc token role mapping độc lập vẫn được xét; fixture chứng minh cả trường hợp có và không có nguồn quyền trùng |

Single-row và SQL list dùng cùng thời điểm decision semantics. Với READ_COMMITTED và nhiều statement,
không hứa một request có snapshot quyền bất biến. Evidence E26/E28/E29/E43.

## 8. Phase 6 — R12, action-token concurrency

### Protocol đề xuất

Serialize theo user bằng row lock trên users trước khi retire và issue action mới. Mọi flow issue,
verify và reset phải thống nhất lock order user → action để tránh deadlock giữa issuance và
consumption. Lookup sơ bộ action để biết user ID chỉ được dùng định vị; sau khi có user lock phải
lock và validate lại action, expiry và consumed_at trước mutation.

Đề xuất invariant một outstanding action cho mỗi user/type. Nếu bổ sung partial unique index trên
(user_id, token_type) WHERE consumed_at IS NULL, retire cả expired rows trước insert. Không dùng
NOW() trong index predicate. Changeset mới phải xử lý duplicate lịch sử theo quy tắc đã review,
ví dụ giữ action mới nhất, consume các action cũ và ghi rõ hiệu ứng với code/link đã gửi.

| Class dự kiến | Kịch bản và assertion |
| --- | --- |
| `ActionTokenIssuanceConcurrencyTest` | Hai transaction issue cùng user/type. Sau commit chỉ một outstanding row theo invariant; action trước bị retire. Rollback không retire nhầm action đang hợp lệ |
| `ActionTokenConsumptionConcurrencyTest` | Hai request dùng cùng action, chỉ một mutation thành công. Test riêng VERIFY_EMAIL và RESET_PASSWORD |
| `ActionTokenIssueConsumeRaceTest` | Issue đồng thời verify/reset action cũ. Assert kết quả theo linearization lock protocol và không deadlock |
| `ActionTokenIsolationTest` | Hai user khác nhau không bị serialize bằng global lock. User/type khác nhau giữ đúng invariant |
| `ActionTokenMigrationTest` | Existing duplicate và expired outstanding rows được xử lý theo migration policy, sau đó index tạo thành công |

Trước sửa, chạy test concurrent issuance để tái hiện R12 hoặc ghi nhận schedule chưa tái hiện được.
Không đánh dấu race đã được reproduce chỉ từ việc đọc source. Evidence E23/E62/E63/E64.

## 9. Phase 7 — R8, mail delivery ngoài business transaction

### Thiết kế đề xuất

Tạo durable mail job cùng transaction user/action. Worker claim bằng lease có thời hạn, commit claim,
gửi SMTP ngoài business transaction rồi đánh dấu kết quả bằng transaction riêng. Retry có backoff,
giới hạn attempts và quan sát failed jobs. Commit rollback không tạo mail job có thể gửi.

Vì worker cần verification token/reset code để gửi, hash hiện tại không đủ để dựng lại nội dung.
ADR phải chốt encrypted payload với key version lấy từ secret store, TTL, retention và quyền truy cập
trước khi triển khai. Chỉ gửi thông tin job/action ID qua notification nếu cần; không phát raw token
vào Kafka business envelope hoặc log. Profile test dùng key riêng, production yêu cầu secret.

Delivery semantics là at-least-once vì SMTP send và commit trạng thái không atomic. Worker retry cùng
job gửi lại cùng action, không issue code mới. Action mới retire action cũ và cancel pending job cũ
trong transaction issuance. Revalidate job/action trước send để giảm stale mail; vẫn phải ghi nhận
race giữa validation và external send. User có thể nhận mail trùng hoặc mail đã stale sau issuance mới.

| Class dự kiến | Kịch bản và assertion |
| --- | --- |
| `IdentityMailTransactionTest` | Commit đăng ký/recovery tạo đúng job; rollback không có mail/job. SMTP chưa bị gọi trong business transaction |
| `IdentityMailWorkerTest` | Claim, send ngoài transaction, retry cùng action, backoff và exhausted attempts. Delivery failure không rollback user đã đăng ký |
| `IdentityMailWorkerConcurrencyTest` | Hai worker không claim đồng thời cùng lease; lease timeout recovery; crash sau send trước ack có thể resend cùng job |
| `IdentityMailSecurityTest` | DB payload không chứa plaintext token/code; encrypted roundtrip đúng key version; wrong key không gửi; expired/cancelled job không gửi; log/error không chứa secret |
| `PasswordRecoveryResponseTest` | Known/unknown/disabled vẫn cùng status contract. Đo timing theo kịch bản thực tế, không dùng sleep để che lệch |

Thêm mail-job changeset, config classification, metrics và runbook cleanup/retry/key rotation.
Schema/transport của phase này là đề xuất mới, chưa có trong baseline. Evidence hiện trạng E17/E19/E65.

## 10. Phase 8 — R9, security audit

### Phạm vi đề xuất

Giữ audit_event transactional cho thay đổi thành công. Bổ sung security outcome cho login success/failure,
refresh reject, password reset, grant denial và các authorization denial cần tra cứu. Chọn taxonomy
có code ổn định và allowlist fields trước triển khai. Không ghi password, token, code, mail body hoặc
request DTO. Subject chưa xác thực phải được đánh dấu riêng, không coi là authenticated Actor.

Failure audit phải có lifecycle riêng để business rollback không xóa dấu vết thất bại. Đề xuất
recorder với transaction độc lập cho security outcome, không lock user/grant row từ transaction
đang lỗi. Khi audit sink unavailable, dùng structured fallback log và metric theo policy đã chốt;
không biến mọi authentication failure thành dependency outage ngoài ý muốn.

Identity success audit phải phản ánh dữ liệu đã commit. Với login success, trả response chỉ sau khi
transaction persistence thành công. Ghi rõ difference giữa attempted outcome và committed mutation.
Nếu chọn durable security_event table mới, bổ sung changeset và retention/access-control runbook.
Các audit requirements và failure policy của phase này cần review trước code.

| Class dự kiến | Kịch bản và assertion |
| --- | --- |
| `IdentitySecurityAuditTest` | Login/register/reset outcome được ghi theo taxonomy; success mutation rollback không có success audit sai |
| `AuthorizationDenialAuditTest` | Denied grant/course operation có failure event, không có business mutation hoặc success audit |
| `SecurityAuditRollbackTest` | Failure event tồn tại sau rollback nghiệp vụ; không deadlock do transaction độc lập; audit sink lỗi theo fallback policy |
| `SecurityAuditRedactionTest` | Captured DB event/log chỉ có allowlist fields; password, access/refresh/action token và reset code không xuất hiện |
| `SecurityAuditConcurrencyTest` | Event ID/idempotency semantics ngăn retry tạo lịch sử mutation sai; phân biệt nhiều attempt hợp lệ với duplicate delivery |

Evidence hiện trạng E27/E50/E51/E52 và identity use cases. Đây là phần mở rộng observability/security
history, không tự suy rằng audit_event hiện ghi mọi failure.

## 11. Commit và acceptance gates

| Commit dự kiến | Phạm vi | Gate |
| --- | --- | --- |
| `fix(identity): resolve native bearer against users table` | R1 + persistence/HTTP regression | Native login/protected/revocation pass với PostgreSQL thật |
| `fix(security): require trusted JWT issuer` | R10 + config/JWT tests | Wrong issuer bị reject dù cùng key/audience |
| `fix(authz): restrict role resolution to system roles` | R5 + token/subject/list tests | Tenant role không bổ sung quyền |
| `refactor(identity)!: namespace authenticated principals` | R4 + migration/compatibility | Collision matrix pass, mapping và rollback reviewed |
| `fix(course)!: align scoped authorization policies` | R2/R3/R11 + REST/GraphQL/SQL tests | Policy đã chốt, detail/list/mutation matrix pass |
| `fix(authz): read committed grant state for decisions` | R6 + transaction/multi-context tests | New decisions thấy grant revocation sau commit |
| `fix(identity): serialize action token lifecycle` | R12 + schema/concurrency tests | Lock order/invariant và rollback pass |
| `feat(identity)!: deliver identity mail from durable jobs` | R8 + worker/migration/security tests | Delivery semantics, encryption và crash tests pass |
| `feat(security): record authentication and access outcomes` | R9 + audit tests | Success/failure semantics và redaction pass |

Mỗi commit có test hồi quy của chính phase đó. Chỉ dùng breaking marker khi phase thực tế thay đổi
API/shared contract/configuration hay hành vi khách hàng đang dựa vào; cập nhật breaking-changes tương ứng.

Trước merge từng phase, chạy targeted JUnit rồi format/build theo convention repo. Build JDK 21 gồm
architecture, classification, context load và API contract drift. Khi wire/schema API đổi, export
contracts rồi check lại. Không hand-edit generated contracts.

Sau P1, cập nhật security review và figure-data theo SHA mới để figures không tiếp tục mô tả source
baseline cũ. Deployment acceptance còn gồm issuer/JWKS topology thật, dữ liệu legacy identity/grants,
replica behavior và latency/query count sau bỏ cache. Các kiểm chứng này không được thay bằng unit test.

## 12. Quyết định còn cần review

1. R2 có yêu cầu global MANAGE cho create hay thêm capability create cho instructor.
2. R4 identity key format, issuer registry và mapping cho external grants/ownership cũ.
3. R6 chấp nhận chi phí DB-per-decision cho P1 và semantics của token-carried rights.
4. R8 encrypted durable mail payload, key management, retention và chấp nhận duplicate/stale delivery.
5. R9 event taxonomy, failure recording/fallback, retention và quyền đọc audit.
6. R12 cách xử lý outstanding duplicate lịch sử nếu tái hiện race hoặc thêm unique index.

P0 R1 độc lập với các lựa chọn thiết kế phía sau và có thể bắt đầu triển khai ngay khi được yêu cầu.


## 13. Kết quả triển khai

ADR 0006 ghi các lựa chọn đã áp dụng theo yêu cầu triển khai. R1/R2/R3/R4/R5/R6/R8/R9/R10/R11/R12
đã có thay đổi executable và regression tests. Các scenario ban đầu trong SecurityRemediationTest (đã tách theo
[plan refactor tests](plan-refactor-security-tests.md)) dùng real HTTP, PostgreSQL,
repository, transaction và authentication path; riêng external SMTP delivery được mock để kiểm tra
transaction/lease/rollback. JwtDecodersTest dùng key ký thật và local JWKS HTTP server.

Các test bổ sung gồm IdentityNamespaceTest, JwtActorMapperTest, IdentityMailCipherTest,
SecurityEventRecorderTest và ActionTokenMigrationTest. Action-token migration có test dữ liệu
duplicate lịch sử, sau đó kiểm tra unique index từ chối outstanding duplicate.

Security event lưu outcome bằng transaction độc lập; identity mutation success audit của register,
verify và reset chứa UUID user đã thay đổi. Mail jobs có trạng thái PENDING/SENT/FAILED/CANCELLED.
Job FAILED giữ ciphertext để có thể retry khi action còn hiệu lực, rồi xóa payload khi action hết
hiệu lực hoặc bị consume; completed records được cleanup sau một ngày.

Test cũng phát hiện và sửa thêm grant RETURNING column-count, binding timestamp cho expiresAt và
flush/translation unique-email error trong registration. Đây là các lỗi trực tiếp trên luồng cần fix.

Chưa có dữ liệu issuer mapping của external subjects cũ trong workspace, chưa chạy migration hay
đổi secret trên production. Cần chuẩn bị mapping/cutover và mail key trước deployment.
Báo cáo remediation và đặc tả figures phản ánh working tree, giữ base SHA làm provenance.
