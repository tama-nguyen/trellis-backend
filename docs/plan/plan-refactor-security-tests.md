# Java code quality và JUnit test refactor

Trạng thái: đã triển khai và xác minh thành công ngày 2026-11-24. Owner đã yêu cầu thực hiện sau review plan.
Áp dụng guideline Java/JUnit được cung cấp ngày 2026-11-24.
Owner đã yêu cầu gom lịch sử và commit implementation cuối theo phases.

## Vấn đề đã xác minh

- `SecurityRemediationTest` ban đầu có 18 scenario trong một class dài hơn 900 dòng, trộn HTTP,
  transaction, fixtures, concurrency và persistence assertions.
- Các test course/native/mail/grant còn gộp nhiều hành vi, setup dài và action trong assertion.
- Một số test đọc JSON bằng `toString().contains`, không kiểm tra đúng field hoặc tập item.
- Test support có query trùng API repository/store đã có; style biến, import, parameter và braces
  chưa thống nhất.
- Các compound JWT/cipher tests trộn nhiều đầu vào lỗi; unit test mail enqueue có tên ngụ ý
  rollback dù thực tế chỉ xác minh exception propagation.

## Constraints

- Chỉ code quality, không đổi nghiệp vụ, authorization, public production API hoặc schema/config.
- Production: kiểu local tường minh, không `var`/`val`; method/constructor parameters `final`.
- Tests và utilities: `val` cho local bất biến; không `var`; parameters `final`. Counter/cursor cần
  gán lại vẫn dùng kiểu tường minh vì `val` là final; không tạo wrapper chỉ để né mutable control state.
- Import tường minh; braces cho conditional/loop; Google Java Format hiện có.
- AAA có khoảng cách hợp lý. `When & Then` cho exception case khi phù hợp; không thêm section rỗng.
- Tên method mô tả hành vi; không lặp tên bằng `@DisplayName` khắp suite.
- Không thêm production dependency, không chuyển integration test sang mock, không bỏ assertion DB.
- Giữ nguyên các thay đổi Actor/OIDC đã có trong working tree trước cleanup này.

## Các phase đã thực hiện

### 1. Baseline và naming

Baseline ba class cũ: 23 test, không failure/error/skipped. Đổi `AspectOrderingIT` và
`notice/NoticeFlowIT` thành `AspectOrderingTest`, `notice/NoticeFlowTest`; không có test filename
hậu tố `IT`. Tài liệu/rules dùng tên hiện tại; report snapshot có commit/hash giữ tên lịch sử.

### 2. Tách responsibility và fixtures

| Phạm vi | Class hiện tại | Kiểm chứng giữ lại |
| --- | --- | --- |
| Native sessions | `identity/NativeAuthenticationTest` | Email/password login; public profile không lộ credentials; refresh; logout kể cả rotated refresh; trạng thái user; expiry boundary; revoked sessions; emailed reset code và revoke-all |
| Registration | `identity/IdentityRegistrationTest` | HTTP register, encrypted queue, conflict không thêm job/audit, real registration rollback, emailed verification enables login |
| Mail worker | `identity/IdentityMailDeliveryTest` | SMTP ngoài transaction, clear completed payload, retry dùng cùng secret, chưa đến lịch không retry, concurrent lease, failed ciphertext retention và expiry cleanup |
| Action concurrency | `identity/ActionTokenConcurrencyTest` | Concurrent issue chỉ một outstanding action; concurrent consume chỉ một thành công và persisted user đã verified |
| Course | `course/CourseAuthorizationTest` | Create permission, native scoped update/activate, deny khác course, scoped REST detail/list và GraphQL detail/list; có tài nguyên đối chứng ngoài scope |
| Role resolution | `security/RoleGrantTest` | Tenant role exclusion, independent resolvers, rollback, uncommitted/committed visibility, expiry và independent token rights |
| Grant management | `security/RoleGrantManagementTest` | Authorization kể cả idempotency replay, actual persisted grant, audit once, revoke và repeated revoke |
| Principal isolation | `security/OidcIdentityIsolationTest` | Cùng UUID không dùng native identity/grants cho OIDC |
| Authentication audit | `security/AuthenticationAuditTest` | Rejected login theo correlation ID và không persist submitted secret |
| Notice reference flow | `notice/NoticeFlowTest` | HTTP publish, outbox/projection, idempotent replay, inbox dedupe, batched GraphQL stats, REST/GraphQL staff privacy |
| JWT/encoding/cipher | `JwtDecodersTest`, `JwtActorMapperTest`, `OidcPrincipalIdEncoderTest`, `IdentityMailCipherTest` | Các input lỗi độc lập, actual accepted claims, golden IDs và các thuộc tính encryption/authentication/redaction |

`CourseFixtures` dùng aggregate và repository thật. `IdentityRegistrationFixtures` gọi real
`RegisterUseCase` cho Given của delivery tests. Rollback test gọi use case thật trong outer
transaction rồi ném exception; không tái dựng business implementation trong test.

`CourseApi`, `IdentityApi`, `RoleGrantApi` chỉ dựng request/parse wire payload. Assertion chính ở
Then. `TestApiClient` giữ readable request construction, headers, execution và `assertJsonBody`
cho fixture preconditions. Không tạo interface/factory hierarchy hoặc persistence framework mới.
GraphQL dùng variables, response kiểm tra field/tập item cụ thể. Các test async dùng real DB,
executor/latch và Awaitility có sẵn; không thêm dependency.

### 3. Dùng repository/store của dự án

| Dữ liệu | API dùng trực tiếp |
| --- | --- |
| Course | `CourseRepository`, `CourseJpaRepository` |
| User/action/session/mail | `UserRepository`, `UserActionTokenJpaRepository`, `IdentityMailJobRepository` |
| Role grants | `RoleGrantStore` |
| Audit trail | `AuditEventStore` |
| Notice stats | `NoticeStatsRepository` |

Đã bỏ `MailJobProbe`, `ActionTokenProbe`, `CourseProbe`, `RoleGrantProbe`. Audit count xử lý đủ
trang hoặc kiểm tra không bị truncated. Grant query xác nhận ID trước revoke rồi kết quả rỗng sau
revoke. Cleanup mail chỉ theo ID thuộc fixture; không xóa toàn bảng hay sửa timestamp bằng SQL.

Ngoại lệ có căn cứ, không tạo production APIs chỉ để phục vụ test:

- `AuditProbe` chỉ đọc `security_event`; hiện có recorder write-only, không có read repository.
- `RoleFixtures` seed tenant role/permission/grant; system-role lookup cố ý không tạo được fixture này.
- `NoticePersistenceProbe` chỉ count/replay outbox; DLQ API và stats repository không thay thế được.
- `ActionTokenMigrationTest` dùng JDBC để kiểm chứng legacy schema, migration và unique index thật.
- Aspect fixture delegate cho production JDBC stores; sink-failure unit test mock JDBC như trước.

### 4. Production style trong phạm vi liên quan

`Actor`, `IdentityOrigin`, `JwtActorMapper`, `OidcPrincipalIdEncoder`, `EventDispatcher`: final
parameters, local type tường minh và spacing. Không đổi thuật toán/claims/actor authority, retry,
transaction, exception hoặc resource-close order.

Encoder giữ `oidc-user`/`oidc-service`, SHA-256, lowercase hex, UTF-8 và input length prefix theo
UTF-16 của Java. Không trim/normalize hoặc thêm separator. Golden vectors gồm Unicode được giữ.

## Verification

Tasks được đối chiếu trong convention/app build. JDK 21 và Docker cho PostgreSQL/Redis.
Formatter và build chạy **tuần tự**, không để task ghi source chạy song song với javac:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew spotlessApply
./gradlew build
```

Build gồm Spotless check, compile với `-Werror`, JUnit/ArchUnit, schema/context và API contract check.
Không thay đổi task hoặc phiên bản dependency.

Targeted runs đã pass trong quá trình refactor: course, native sessions, mail/registration,
role/security, JWT/encoder/concurrency và notice. Số test tăng do tách scenario và parameterize;
không dùng số lượng test làm bằng chứng duy nhất về coverage. Bảng phía trên đối chiếu các outcome
cũ, gồm DB effects, time/permissions boundaries và integration behavior.

Audit source bằng Java AST chạy ngoài repository kiểm tra: explicit imports, final parameters,
braces, không `var`, không production `val`, không block workflow trong `assertThrows`. Kết quả
và log build cuối được ghi dưới đây. Công cụ tạm không được thêm vào repo hoặc production build.

Declarative ArchUnit rules và context-start guard giữ contract kiểm chứng vốn có; không thêm fake
assertion chỉ để đủ template. Test identity mail enqueue được đổi tên đúng exception propagation;
không dùng unit mock để tuyên bố đã chứng minh transaction rollback.

## Kết quả cuối đã thực thi

| Check | Command / evidence | Kết quả |
| --- | --- | --- |
| Formatter | `./gradlew spotlessApply` | BUILD SUCCESSFUL in 811ms |
| Build và checks | `./gradlew build` | BUILD SUCCESSFUL in 59s |
| JUnit/ArchUnit | XML các module dưới `build/test-results/test/` | 176 tests, 0 failure/error/skipped |
| API contracts | `:trellis-app:checkApiContracts` trong build | 1 check, 0 failure/error/skipped |
| Style source audit | Java AST audit ngoài repo | PASS cho imports, parameters, braces, var/val, assertThrows |
| Diff | `git diff --check` | Exit 0 |
| Snapshot | SHA-256 của 65 Java sources trước/sau final build | Không file Java nào thay đổi trong lúc verification |

Module totals:

- `trellis-app:test`: 129 tests.
- `trellis-app:checkApiContracts`: 1 tests.
- `trellis-common:test`: 2 tests.
- `trellis-identity:test`: 43 tests.
- `trellis-platform-events:test`: 2 tests.

Bản copy XML, formatter/build log, source hashes và summary JSON ở ngoài git:

`/var/folders/2m/gc4r0tcj03148hl3f57d5vjh0000gn/T/trellis-quality-evidence-k1yaxlxz`

Report app: `trellis-app/build/reports/tests/test/index.html`. Không chạy production migration,
IdP/SMTP thật, không sửa generated contracts hoặc dependency/config, không commit.

## Files trong scope cleanup

Production: `Actor.java`, `IdentityOrigin.java`, `JwtActorMapper.java`,
`OidcPrincipalIdEncoder.java`, `EventDispatcher.java`. Các thay đổi Actor extraction có sẵn được
bảo toàn; cleanup này chỉ chuẩn hóa code style quanh implementation đó.

Tests/utilities: `trellis-app/src/test/java` (course, identity, security, notice, platform tests,
contract export, architecture và support); `trellis-identity/src/test/java` (use cases, support,
cipher và fixtures); common test/vector fixtures; platform-events envelope contract test.

Không phát hiện lỗi business mới cần sửa trong scope này. Ngoại lệ SQL và pure context/guard
contracts đã giải thích ở trên; không thay bằng giả lập hoặc assertion yếu chỉ để làm ngắn test.


## Follow-up: use cases và action-token migration

Đã rà 27 use cases và 8 application helpers thuộc identity, course, notice và platform:
đặt tên private dependencies theo trách nhiệm, tách các bước workflow, dùng explicit local types
trong production và `final` parameters. Đồng bộ tên dependencies trong 10 identity unit tests.
Giữ nguyên public method/parameter names phục vụ SpEL, annotations, transaction boundaries,
exception handling, thứ tự side effects và API/persistence contracts.

`ActionTokenMigrationTest` được tách thành hai test độc lập với PostgreSQL container riêng:

- Migration giữ đúng một action mới nhất khi dữ liệu lịch sử trùng.
- Unique index từ chối action còn hiệu lực thứ hai với SQLSTATE `23505`; dữ liệu đang có vẫn nguyên.

Lifecycle JDBC chuyển vào `@BeforeEach`/`@AfterEach`; fixture và migration execution là private
helpers có tên rõ ràng. Test methods theo Given/When/Then, dùng `val`, final parameters,
explicit imports và lambda ngắn cho `assertThrows`. SQL legacy vẫn cần thiết để kiểm chứng
migration trên schema cũ; không thay bằng repository của schema hiện tại hay mock.

Verification của follow-up:

- `./gradlew :trellis-identity:test :trellis-app:test`: BUILD SUCCESSFUL in 1m 1s.
- `./gradlew :trellis-app:test --tests io.trellis.app.ActionTokenMigrationTest`:
  BUILD SUCCESSFUL in 7s; 2 tests, 0 failures/errors/skipped.
- `./gradlew spotlessApply`: BUILD SUCCESSFUL in 871ms.
- Style AST audit: PASS cho 35 production files trong scope và `ActionTokenMigrationTest`.
- Lần build tổng đầu tiên dừng tại Spotless do một dòng trống trong import của `CourseAccess`;
  formatter đã sửa. Lần tiếp theo có 8 course tests nhận HTTP 404; migration tests vẫn pass.
- Chạy riêng `./gradlew :trellis-app:test --tests io.trellis.app.course.CourseAuthorizationTest`:
  BUILD SUCCESSFUL in 20s, 9 tests pass. Chưa xác định nguyên nhân HTTP 404 trong lần chạy tổng;
  không sửa assertion hoặc kết luận nguyên nhân khi chưa có bằng chứng.
- Lần chạy tổng tiếp theo: 177 tests pass (app 130, identity 43, common 2, events 2),
  API contract check 1 pass. Build vẫn dừng ở Spotless vì một dòng trống trong imports của
  `OrderProbeService`; đã chạy formatter để sửa. Không coi lần build này là BUILD SUCCESSFUL.
- Sau formatter: `./gradlew spotlessCheck` BUILD SUCCESSFUL in 1s; `git diff --check` exit 0.
  Build/test logs: `/tmp/trellis-migration-refactor-build.log`,
  `/tmp/trellis-course-refactor-check.log`, `/tmp/trellis-migration-refactor-recheck.log`,
  `/tmp/trellis-migration-refactor-style-check.log`. Không commit.


## Chốt lịch sử theo phases

Chín commit remediation trung gian và working tree sau refactor được gom từ base
`0ab180b6ffcf2d2746f455f02f9142217a47d266`. Giữ code và regression tests tương ứng trong cùng
phase, không giữ các phiên bản test bị thay thế giữa các lần review:

| Phase | Commit | Nội dung |
| --- | --- | --- |
| 1 | `2faee3a` | Trusted principals, issuer, current grants, security outcomes và schema prerequisites |
| 2 | `9181d38` | Native sessions, serialized actions, durable mail và identity/security integration tests |
| 3 | `1895e6b` | Scoped course permissions cùng REST/GraphQL regression tests |
| 4 | `d756dc4` | Conventions của reference workflows, platform và contract tests |
| 5 | Commit tài liệu cuối | ADRs, rollout, plans và verification evidence |

Verification trước khi viết lại lịch sử: JDK 21 `./gradlew build`, BUILD SUCCESSFUL in 1m 2s;
177 tests, zero failures/errors/skipped và API contract check pass.
Log: `/tmp/trellis-phases-verified-build.log`.
Đây là kết quả mới nhất thay cho trạng thái build chưa pass trong các lần kiểm chứng trước ở trên.
Lịch sử cũ và snapshot đầy đủ đã được lưu bằng Git bundle bên ngoài repository trước khi reset;
không push hoặc sửa remote branch.

Kiểm tra từng checkpoint trong worktree tách biệt:

- Phase 1 `2faee3a`: `./gradlew :trellis-app:test --tests io.trellis.app.JwtActorMapperTest
  --tests io.trellis.app.JwtDecodersTest --tests io.trellis.app.OidcPrincipalIdEncoderTest
  --tests io.trellis.app.SecurityEventRecorderTest --tests io.trellis.app.ActionTokenMigrationTest
  --tests io.trellis.app.notice.NoticeFlowTest :trellis-identity:test
  --tests io.trellis.identity.application.usecase.profile.GetCurrentUserUseCaseTest`:
  BUILD SUCCESSFUL in 26s.
- Phase 2 `9181d38`: `./gradlew :trellis-app:test --tests 'io.trellis.app.identity.*'
  --tests 'io.trellis.app.security.*' :trellis-identity:test`: BUILD SUCCESSFUL in 26s.
- Phase 3 `1895e6b`: `./gradlew :trellis-app:test
  --tests io.trellis.app.course.CourseAuthorizationTest`: BUILD SUCCESSFUL in 20s; 9 tests pass.
- Phase 4 `d756dc4`: `./gradlew build` trong worktree tách biệt:
  BUILD SUCCESSFUL in 1m 2s; 177 tests, zero failures/errors/skipped; API contract check 1 pass.
- Đối chiếu tree sau phase 4 với snapshot trước khi gom: toàn bộ source và test blobs không đổi.
  Các chỉnh sửa bổ sung của phase 5 chỉ cập nhật tài liệu, trạng thái plans và quy ước test names.


## Đồng bộ skills, rules và conventions

Theo yêu cầu owner, phase tài liệu bao gồm conventions Java/JUnit và skill `java-refactor`;
review/new-module skills cùng AGENTS.md và architecture guide trỏ tới hướng dẫn hiện tại.
Bỏ rule cấm JPA bị mâu thuẫn: Spring Data JPA được hỗ trợ cho business persistence, repository
queries vẫn theo quy ước native SQL. Không thay production/test sources hoặc build configuration.

Verification của phần tài liệu: skill-creator `quick_validate.py` PASS cho `java-refactor`,
`review`, `new-module`; 21 relative Markdown link targets tồn tại; `./gradlew spotlessCheck`
BUILD SUCCESSFUL in 825ms; `git diff --check` exit 0. Không chạy lại JUnit vì chỉ sửa Markdown;
kết quả 177 tests của phase 4 ở trên vẫn là kết quả test đã thực thi cho source không đổi.
Các cập nhật này được gộp vào commit tài liệu phase 5, giữ nguyên tổng số 5 commit sau base.
