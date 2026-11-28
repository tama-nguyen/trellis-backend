# Tách OIDC principal ID encoding khỏi Actor

Jira: [TL-65](https://tama-nguyen.atlassian.net/browse/TL-65)

Historical UUID-native-ID wording is superseded by ADR 0004 (2026-11-27). Technical OIDC encoding
and its recorded verification remain unchanged.

Ngày 2026-11-24. Hướng extraction đã được owner chọn và yêu cầu triển khai.
Baseline nghiên cứu ban đầu là snapshot remediation trước khi gom lịch sử.
Implementation cuối nằm trong phase security `2faee3a`, trên branch `feat/user-permission`
với base `0ab180b6ffcf2d2746f455f02f9142217a47d266`. Snapshot cũ được lưu trong backup ngoài repository.

## Vấn đề và mục tiêu

`Actor` hiện giữ principal data, invariants, OIDC factories, namespace constants và SHA-256 encoding.
`JwtActorMapper` gọi các factories này khi nhận JWT đã validate. Native authentication và event
consumer dùng các factory generic. `actor.id()` được dùng để lookup grants và ghi ownership.

Evidence baseline nằm tại `Actor.java:20–59`, `JwtActorMapper.java:33–51`,
`NativeTokenAuthenticationProvider.java:53`, `GrantSubject.java:17–22` và
`CreateCourseUseCase.java:57`. Module map trong AGENTS.md giao JWT mapping cho platform.

Mục tiêu là đưa encoding về `trellis-platform/security/jwt` và thu gọn `Actor` về model cùng
invariants. Slice này được triển khai trên phần remediation đã commit theo phases.

## Contract và giới hạn

- Giữ chính xác prefix, delimiter, SHA-256, UTF-8 input và lowercase hexadecimal output hiện tại.
- Giữ length prefix theo Java String.length, gồm cách tính UTF-16 cho Unicode.
- Giữ native UUID, external actor IDs, role/permission sets và IdentityOrigin hiện tại.
- Giữ semantics của Actor.user/service/system/anonymous và constructor UserActor ba tham số.
- Giữ loại exception cho null/blank encoder input. Error message wording không là contract.
- Cập nhật toàn bộ caller trong repository khi bỏ Actor.oidcUser/oidcService.
- Không thêm Spring bean/config/dependency cho encoder stateless.
- Không sửa schema, grants, ownership, token claims, validation policy hoặc event envelope.

Gap provenance khi EventDispatcher dựng lại USER actor đã được ghi nhận ở research. Việc đổi
event contract/default origin cần một slice riêng với acceptance criteria và compatibility review.

## Thiết kế đã chọn

| Component | Trách nhiệm |
| --- | --- |
| `OidcPrincipalIdEncoder` trong platform/security/jwt | Private namespace/algorithm constants, userId/serviceId và encoding deterministic |
| `JwtActorMapper` | Chọn identity từ JWT, gọi encoder rồi tạo actor với provenance rõ ràng |
| `Actor` trong common/context | Principal data, immutable collections, ID/origin invariants và generic factories |

Encoder là final utility class với private constructor và hai entrypoints userId/serviceId.
Generic user factory có overload nhận IdentityOrigin để mapper biểu diễn dữ liệu đã xác thực.
Caller hiện dùng factory ba tham số tiếp tục có behavior hiện tại.

## Các phase trong slice

### Phase A — Baseline và regression trước extraction

Inspect Actor, mapper, native provider, event dispatcher, grant subject, profile lookup và các caller.
Chạy targeted tests hiện có. Thêm fixed-vector tests cho user/service IDs và Unicode issuer trên
implementation cũ, cùng test null/blank input. Chạy để xác nhận vectors trước khi chuyển code.

Acceptance gồm deterministic output và exception types khớp implementation cũ.
Stop trigger là baseline failure chưa giải thích được hoặc kết quả khác expected vector.

### Phase B — Extraction và cập nhật caller

Thay đổi `Actor.java`, thêm `OidcPrincipalIdEncoder.java`, cập nhật `JwtActorMapper.java`.
Chuyển namespace tests thành `OidcPrincipalIdEncoderTest`. Integration fixtures lấy expected
principal ID qua encoder. Mapper test kiểm tra provenance, roles và permissions sau extraction.

Acceptance gồm common không còn hashing/OIDC-specific factory, tất cả caller compile, cùng input
trả đúng ID của Phase A và native factory giữ behavior. Stop nếu cần thay đổi ID format, authority
resolution hoặc persistence/event contract để hoàn thành extraction.

### Phase C — Verification và tài liệu

Chạy lại targeted tests và build JDK 21. Kiểm tra diff trong phạm vi slice và API contract checks.
Cập nhật runbook đang hướng dẫn tính ID qua Actor factory sang encoder. Ghi rõ source-level API
change của factory OIDC mới thêm ở remediation, cùng chứng cứ ID compatibility.

## Commands đã đối chiếu repository

Task `:trellis-app:test` có cấu hình tại trellis-app/build.gradle.kts. `spotlessApply` và `build`
được khai báo qua convention plugins và đã chạy ở remediation.

```bash
export JAVA_HOME=/Users/tam/Library/Java/JavaVirtualMachines/ms-21.0.12.1/Contents/Home

# Trước extraction
./gradlew :trellis-app:test \
  --tests io.trellis.app.IdentityNamespaceTest \
  --tests io.trellis.app.JwtActorMapperTest \
  --tests io.trellis.app.security.OidcIdentityIsolationTest.oidcUuidCannotReadNativeProfileOrNativeGrants

# Sau extraction
./gradlew :trellis-app:test \
  --tests io.trellis.app.OidcPrincipalIdEncoderTest \
  --tests io.trellis.app.JwtActorMapperTest \
  --tests io.trellis.app.security.OidcIdentityIsolationTest.oidcUuidCannotReadNativeProfileOrNativeGrants

./gradlew spotlessApply build
```

HTTP regression dùng PostgreSQL Testcontainers và test JWT keys, cần Docker. Các command trên
không gọi production hoặc IdP trả phí. Kết quả thực thi được ghi sau khi chạy.

## Done

Fixed vectors pass trước/sau extraction, mapper và HTTP regression pass, full build pass,
mọi caller được migrate, tài liệu migration chỉ đúng entrypoint và không có migration dữ liệu
mới do slice này. Các thay đổi sau checkpoint remediation giữ trong working tree để review.

## Kết quả thực thi

Đã hoàn thành Phase A/B/C trong working tree trên baseline đã ghi ở đầu plan.

| Kiểm chứng | Command đã chạy với JDK 21 | Kết quả quan sát | Log |
| --- | --- | --- | --- |
| Baseline | `./gradlew :trellis-app:test --tests io.trellis.app.IdentityNamespaceTest --tests io.trellis.app.JwtActorMapperTest --tests io.trellis.app.SecurityRemediationTest.oidcUuidCannotReadNativeProfileOrNativeGrants` | `BUILD SUCCESSFUL in 18s` | `/tmp/actor-extraction-baseline.log` |
| Fixed vectors trên code cũ | `./gradlew :trellis-app:test --tests io.trellis.app.IdentityNamespaceTest --tests io.trellis.app.JwtActorMapperTest` | `BUILD SUCCESSFUL in 2s` | `/tmp/actor-extraction-vectors-before.log` |
| Sau extraction | `./gradlew :trellis-app:test --tests io.trellis.app.OidcPrincipalIdEncoderTest --tests io.trellis.app.JwtActorMapperTest --tests io.trellis.app.SecurityRemediationTest.oidcUuidCannotReadNativeProfileOrNativeGrants` | `BUILD SUCCESSFUL in 20s` | `/tmp/actor-extraction-after.log` |
| Full verification | `./gradlew spotlessApply build` | `BUILD SUCCESSFUL in 1m` | `/tmp/actor-extraction-build.log` |

JUnit XML sau full build ghi 83 app tests, 37 identity tests, 2 common tests và 2 event-platform tests,
tổng 124 tests với 0 failures/errors. Encoder có 4 test methods. Architecture, classification,
context load và API contract checks đều nằm trong build đã chạy.

Đã kiểm tra repository production/test callers không còn gọi Actor.oidcUser/oidcService.
Actor giảm từ 161 xuống 132 dòng và bỏ crypto imports/namespace constants. Encoder có 46 dòng,
không có Spring dependency hoặc mutable shared state. Mapper giữ quyết định user/service và
provenance hiện tại. Runbook, ADR 0006 và breaking-changes đã cập nhật source-level entrypoints.

Chín commit remediation ban đầu và các thay đổi sau review đã được gom lại theo phases.
Slice extraction đã nằm trong commit security `2faee3a`; không tạo commit refactor bổ sung cho slice này.
Gap event provenance được giữ trong phạm vi research đã ghi, để review như một thay đổi contract riêng.

Các command trong bảng evidence giữ nguyên tên class tại thời điểm đã chạy. Các scenario hiện tại
được tổ chức theo [plan refactor tests](../TL-117/plan-refactor-security-tests.md).
