# Trellis Backend — Configuration & Spring Profiles Guide

Hướng dẫn cấu hình cho local development, automated tests và production deployment.

## 1. Cấu hình chung và override môi trường

`application.yml` chọn profile mặc định, khai báo giá trị nền và import các file theo chức năng.
`application-dev.yml`, `application-test.yml` và `application-prod.yml` chỉ chứa giá trị khác nhau
giữa các môi trường. Khi sửa giá trị chung, sửa tại file chức năng tương ứng.

`setup` và `migrate` là operational profile, đặt sau environment profile. Production secrets lấy
từ environment hoặc secret store. Prod yêu cầu Redis cho rate limiting. Event transport hiện vẫn
mặc định inmemory, cần chọn Kafka khi deployment yêu cầu distributed transport.

```text
resources/
├── application.yml
├── application-dev.yml
├── application-test.yml
├── application-prod.yml
├── application-setup.yml
├── application-migrate.yml
└── config/
    ├── persistence.yml
    ├── security.yml
    ├── identity.yml
    ├── rate-limit.yml
    ├── events.yml
    ├── observability.yml
    └── web.yml
```

## 2. Trách nhiệm từng file

| File | Nội dung |
| --- | --- |
| `application.yml` | Profile selection, explicit imports, application lifecycle, feature flags và setup defaults |
| `config/persistence.yml` | Pool size, JPA/schema validation, transaction retry, idempotency và migration defaults |
| `config/security.yml` | Token claims và authorization cache |
| `config/identity.yml` | Native token/code lifetimes, BCrypt và default role |
| `config/rate-limit.yml` | Shared policy bandwidths, HTTP quotas, trusted proxy setting và revision |
| `config/events.yml` | Relay, consumer và retention defaults |
| `config/observability.yml` | Management endpoints, telemetry và common logging |
| `config/web.yml` | HTTP errors, GraphQL routing/limits và common OpenAPI settings |
| `application-dev.yml` | Local connections/credentials, GraphiQL, introspection và dev telemetry |
| `application-test.yml` | Test-specific values, Testcontainers datasource, disabled relay và in-memory stores |
| `application-prod.yml` | Deployment endpoints, required secrets, production stores và exposure settings |
| `application-setup.yml` | Schema/infrastructure setup, disabled web và relay |
| `application-migrate.yml` | Migration execution, disabled web và relay |

Giữ credentials, issuer, audience, public frontend URI và địa chỉ hạ tầng ở environment profile.
Giá trị shared chỉ lấy từ các key có cùng giá trị trong cả ba môi trường. Một property chỉ tồn tại
trong một hoặc hai profile tiếp tục thuộc profile đó. Test datasource URL do Testcontainers cung cấp.

## 3. Thứ tự nạp và quy tắc chỉnh sửa

Spring Boot nạp `application.yml` và các resource trong `spring.config.import`, sau đó áp dụng
các file theo active profile. Import shared files chỉ khai báo tại `application.yml`.
Các import được yêu cầu tồn tại để lỗi tên file được phát hiện lúc khởi động.

```text
TRELLIS_PROFILE=dev
  application.yml + config/*.yml → application-dev.yml

TRELLIS_PROFILE=prod,setup
  application.yml + config/*.yml → application-prod.yml → application-setup.yml

TRELLIS_PROFILE=prod,migrate
  application.yml + config/*.yml → application-prod.yml → application-migrate.yml
```

Profile cuối override property trùng tên ở profile trước. Dùng một environment profile, thêm
operational profile khi chạy setup/migrate. Environment variables và command-line options tiếp tục
có thể override giá trị YAML theo thứ tự ưu tiên của Spring Boot.

Khi thêm cấu hình chung, chọn file chức năng sở hữu nó và khai báo `trellis.*` trong
`SensitiveProperties`. `ConfigClassificationTest` quét cả `application*.yml` và `config/*.yml`.
`ConfigurationProfilesTest` kiểm tra imports, environment overrides và operational overrides.
Comment giải thích ràng buộc vận hành. Hướng dẫn dài đặt trong tài liệu này hoặc runbook chuyên biệt.

```yaml
# Dev profile supplies a local default.
url: ${TRELLIS_DB_URL:jdbc:postgresql://localhost:15432/trellis}

# Production requires deployment input.
password: ${TRELLIS_DB_PASSWORD}
```

## 4. Quick start: chạy backend local (`dev`)

### 4.1 Điều kiện trước khi chạy

- Có Java/Gradle phù hợp với repository và dùng Gradle Wrapper (`./gradlew`).
- PostgreSQL local sẵn sàng, hoặc stack Docker Compose tương ứng đã chạy.
- Nếu sử dụng Keycloak, Kafka, Redis, S3/MinIO, OTLP tracing: khởi động **những service thực sự cần thiết** cho use case đang test. Các YAML có giá trị mặc định local, không tự khởi chạy service.
- Tham khảo `build-logic/docker/compose.yml` hoặc file Compose được repository thực tế sử dụng; kiểm tra đúng đường dẫn và service name trước khi chạy.

Trong `application-dev.yml`, PostgreSQL mặc định:

```yaml
spring:
  datasource:
    url: ${TRELLIS_DB_URL:jdbc:postgresql://localhost:15432/trellis}
    username: ${TRELLIS_DB_USER:trellis}
    password: ${TRELLIS_DB_PASSWORD:trellis}
```

Nếu ứng dụng được chạy trực tiếp trên máy host, `localhost:15432` là endpoint mặc định. Nếu chạy **bên trong container**, `localhost` trỏ về chính container đó — phải dùng hostname/network của Docker Compose hoặc override `TRELLIS_DB_URL`.

### 4.2 Lệnh chạy

Chạy từ root của Gradle multi-project (nếu `trellis-app` khai báo `bootRun`):

```bash
# Default từ application.yml đã là dev
./gradlew :trellis-app:bootRun

# Hoặc chọn rõ môi trường
TRELLIS_PROFILE=dev ./gradlew :trellis-app:bootRun

# Ví dụ override database bằng môi trường (không chỉnh YAML)
TRELLIS_PROFILE=dev \
TRELLIS_DB_URL='jdbc:postgresql://localhost:15432/trellis' \
TRELLIS_DB_USER='trellis' \
./gradlew :trellis-app:bootRun
```

Trong IntelliJ IDEA, tạo Run Configuration cho application/Gradle và thêm environment variable `TRELLIS_PROFILE=dev` (cộng các biến cần thiết khác); không thay đổi mặc định `application.yml` mỗi khi chuyển máy.

### 4.3 Kiểm tra local

Các endpoint/config được khai báo trong `dev`:

| Mục đích | Giá trị mặc định | Lưu ý |
| --- | --- | --- |
| API | `http://localhost:8080` | `TRELLIS_SERVER_PORT` có thể override |
| GraphQL API | `/api/graphql` | Endpoint cần xác thực theo security của ứng dụng |
| GraphiQL | `http://localhost:8080/graphiql` | Chỉ bật trong `dev` |
| OpenAPI | `http://localhost:8080/v3/api-docs` | Bật ở `dev` và `test` |
| Actuator | `http://localhost:8081/actuator/health` | Management port riêng; không đưa ra public internet |
| Liveness/Readiness | `/actuator/health/liveness`, `/actuator/health/readiness` | Trên management port |
| Keycloak issuer | `http://localhost:18180/realms/trellis` | Phải có IdP phù hợp khi test authenticated flows |

```bash
curl -i http://localhost:8081/actuator/health
curl -i http://localhost:8080/v3/api-docs
```

Port mặc định có thể đổi qua environment. Các ví dụ HTTP ở đây chỉ áp dụng khi app đã khởi động và service/route tương ứng thực sự hoạt động.

## 5. Automated tests (`test`)

`application-test.yml` cố ý **không** cấu hình `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password` mặc định; test infrastructure (ví dụ Testcontainers + `@DynamicPropertySource` / `@ServiceConnection` nếu test code sử dụng) cung cấp chúng khi chạy.

Những khác biệt quan trọng trong YAML hiện tại:

- Liquibase context: `test`.
- Events transport: `inmemory`.
- Relay: bị tắt để test chủ động điều khiển khi cần.
- OpenAPI được bật để hỗ trợ export API contract.
- Không cấu hình OIDC issuer mặc định; các đường dẫn `/error` và `/v3/api-docs` được khai báo public.

```bash
# Chạy test task của module nếu Gradle project có task tương ứng
./gradlew :trellis-app:test
```

**Không chạy backend lâu dài với `TRELLIS_PROFILE=test`** và kỳ vọng nó kết nối được một PostgreSQL cố định. Test profile được thiết kế cho test runtime; datasource chỉ có khi test setup cấp nó. Nếu test lỗi vì thiếu DB, kiểm tra Testcontainers/Docker và test wiring trước khi thêm JDBC URL vào YAML.

## 6. Production (`prod`)

File `application-prod.yml` giữ các thông số môi trường ở dạng placeholder. Tối thiểu, cấu hình database trong bản hiện tại yêu cầu:

```yaml
spring:
  datasource:
    url: ${TRELLIS_DB_URL}
    username: ${TRELLIS_DB_USER}
    password: ${TRELLIS_DB_PASSWORD}
```

Ví dụ chuẩn bị runtime (minh họa, không dùng credentials thật trong shell history hoặc repository):

```bash
export TRELLIS_PROFILE=prod
export TRELLIS_DB_URL='<database-jdbc-url>'
export TRELLIS_DB_USER='<database-user>'
# TRELLIS_DB_PASSWORD nên được inject từ secret manager / orchestrator
export TRELLIS_OIDC_ISSUER_URI='<oidc-issuer-url>'
export TRELLIS_CURSOR_SECRET='<secret-at-least-16-bytes>'

# Khi thực sự sử dụng Kafka cluster và Redis shared store:
export TRELLIS_EVENTS_TRANSPORT=kafka
export TRELLIS_KAFKA_BOOTSTRAP='<broker-host:port>'
export TRELLIS_RATE_LIMIT_STORE=redis
export TRELLIS_REDIS_URI='<redis-uri>'

# Khởi chạy artifact đã build bằng quy trình deploy của dự án.
# Ví dụ khi tồn tại executable jar:
java -jar /path/to/trellis-app.jar
```

**Không dùng chuỗi `'<...>'` làm cấu hình thật.** Việc set secret trực tiếp bằng shell export chỉ để minh họa tên biến; production nên dùng secret store/Kubernetes Secret/CI secret injection với kiểm soát truy cập phù hợp.

### 6.1 Những giá trị phải quyết định trước khi deploy

| Property | Hành vi bản YAML `prod` hiện tại | Việc cần xác nhận |
| --- | --- | --- |
| `trellis.events.transport` | `${TRELLIS_EVENTS_TRANSPORT:inmemory}` | Nếu nhiều instance/cần event streaming, phải cấu hình Kafka có chủ đích |
| `trellis.rate-limit.store` | `${TRELLIS_RATE_LIMIT_STORE:memory}` | Nếu cần rate limit toàn cluster, cấu hình Redis |
| `spring.kafka.bootstrap-servers` | `${TRELLIS_KAFKA_BOOTSTRAP:}` | Cấp endpoint khi transport Kafka |
| `trellis.rate-limit.redis.uri` | `${TRELLIS_REDIS_URI:}` | Cấp Redis URI khi store Redis |
| `trellis.security.issuer-uri` | Có fallback đến Spring OAuth2 issuer property, sau đó có thể rỗng | Bảo đảm issuer hợp lệ / authentication được cấu hình đúng |
| `trellis.pagination.cursor-secret` | `${TRELLIS_CURSOR_SECRET}` | Secret đủ mạnh, kiểm soát rotation |
| `trellis.storage.s3.*` | Nhiều key có default rỗng | Inject khi feature storage thực sự cần dùng |
| `springdoc.api-docs.enabled` | Mặc định `false` | Chỉ bật theo chính sách bảo mật/triển khai |

**Quan trọng:** Tách YAML là một thay đổi về tổ chức config, **không phải** quyết định chuyển production sang Kafka, Redis, hay bắt buộc S3. Các quyết định đó cần được xử lý riêng, kiểm thử và review.

## 7. Setup và migrate: hai operational profiles

Hai file này **không chứa environment credentials**. Luôn chọn profile theo thứ tự `[environment],[operation]`:

| Mục đích | Profile | Kết quả mong đợi theo cấu hình |
| --- | --- | --- |
| Tạo/cập nhật schema local, chạy bootstrap có cấu hình | `dev,setup` | Không mở web server, bật Liquibase và setup mode, tắt event relay |
| Setup hạ tầng/schema production | `prod,setup` | Dùng production dependencies, chạy one-shot setup |
| Chạy migration steps trên production | `prod,migrate` | Không mở web server, bật migration mode, tắt relay |

```bash
# Ví dụ từ Gradle, dùng đúng profile cho tác vụ
TRELLIS_PROFILE=dev,setup ./gradlew :trellis-app:bootRun

# Production: dùng artifact và secret injection của deployment
TRELLIS_PROFILE=prod,setup java -jar /path/to/trellis-app.jar
TRELLIS_PROFILE=prod,migrate java -jar /path/to/trellis-app.jar
```

Chi tiết hiện tại:

```yaml
# application-setup.yml (rút gọn)
spring:
  main:
    web-application-type: none
  liquibase:
    enabled: true
trellis:
  setup:
    enabled: true
  events:
    relay:
      enabled: false
```

```yaml
# application-migrate.yml (rút gọn)
spring:
  main:
    web-application-type: none
trellis:
  migration:
    run-on-startup: true
  events:
    relay:
      enabled: false
```

`setup` **có thể** tạo Kafka topics hoặc S3 bucket khi được bật qua `TRELLIS_SETUP_KAFKA_TOPICS=true` hoặc `TRELLIS_SETUP_S3_BUCKET=true` tương ứng; mặc định các cờ này là `false` trong environment YAML hiện tại. Khả năng thực thi còn phụ thuộc implementation và quyền truy cập tài nguyên.

> Cảnh báo vận hành: `prod,setup` và `prod,migrate` có thể thay đổi schema/data/tài nguyên. Chỉ chạy trong deployment job được kiểm soát, có backup/rollback plan, quyền truy cập và quy trình review. Không chạy thử với production credentials từ máy cá nhân.

## 8. Khi thêm một property mới, sửa file nào?

1. Xác định nó thuộc **environment behavior/dependency** hay **operation mode**.
2. Property dùng để chạy app bình thường: khai báo trong từng environment có nhu cầu (`dev`, `test`, `prod`). Không đưa vào `application.yml`. Có thể lặp lại cùng một giá trị ở nhiều môi trường — đây là chủ đích thiết kế.
3. Property chỉ thay đổi *cách chạy* setup/migrate (như tắt web/relay): đặt trong `application-setup.yml` hoặc `application-migrate.yml`.
4. Với endpoint, account, password, token và secret: dùng placeholder hoặc secret injection trong production; default local chỉ đặt ở `dev` khi thật sự an toàn.
5. Xem lại `SensitiveProperties` và guardrail tests của `trellis.*` khi thêm key mới; bảo đảm logging/Actuator không làm lộ dữ liệu nhạy cảm.
6. Kiểm tra toàn bộ profile liên quan, đặc biệt tổ hợp `prod,setup` / `prod,migrate`, rồi update tài liệu và deployment env.

**Ví dụ:** Thêm timeout cho một HTTP client ở mỗi environment:

```yaml
# application-dev.yml
# (nằm dưới đúng namespace của service)
trellis:
  integration:
    partner-api:
      connect-timeout: 3s

# application-prod.yml
trellis:
  integration:
    partner-api:
      connect-timeout: ${TRELLIS_PARTNER_API_CONNECT_TIMEOUT:2s}
```

Ví dụ chỉ minh họa **quy tắc đặt config**; key `trellis.integration.partner-api.connect-timeout` chưa được xác nhận có binder/code hỗ trợ. Không copy example này vào source nếu chưa implement property.

**Quy tắc khi sửa YAML:** Đừng thêm hai top-level `spring:` hay `trellis:` trùng nhau trong cùng tài liệu YAML; phải merge vào mapping hiện hữu. Tránh thay đổi logic runtime chỉ vì muốn giảm số dòng config.

## 9. Troubleshooting

| Triệu chứng | Nguyên nhân nên kiểm tra | Hành động |
| --- | --- | --- |
| App chạy nhầm local config trên production | Không set `TRELLIS_PROFILE` nên mặc định là `dev` | Set rõ `TRELLIS_PROFILE=prod` ở deployment |
| Error `Could not resolve placeholder` | Một biến `${VAR}` không có giá trị | Cấp env/secret đúng tên; xem profile đang active |
| `Connection refused` đến PostgreSQL | DB chưa chạy, sai port/host; `localhost` sai trong container | Xác minh Docker Compose, hostname và `TRELLIS_DB_URL` |
| App không truy cập được GraphiQL | Đang dùng `test`/`prod`, hoặc sai path/port | Kiểm tra `dev`, port 8080, đường dẫn `/graphiql` |
| Kafka không có event | Transport đang là `inmemory` | Kiểm tra `TRELLIS_EVENTS_TRANSPORT` và broker |
| Rate limit khác nhau giữa các instance | Store đang là `memory` | Xem xét Redis cho shared rate limiting |
| Setup/migration có mở web hoặc không chạy tác vụ | Chọn sai profile/thứ tự hoặc runtime bị override | Dùng `prod,setup` / `prod,migrate`, xem effective config |
| Tests không tìm thấy datasource | Testcontainers/test wiring chưa cấp datasource | Kiểm tra Docker/Testcontainers và test annotations |
| Prod không xác thực được JWT | OIDC issuer/JWKS/audience không khớp IdP | Kiểm tra env và security config; tuyệt đối không tắt auth để né lỗi |

Để debug việc Spring Boot nạp configuration file (không bật lâu dài trên production):

```bash
TRELLIS_PROFILE=dev ./gradlew :trellis-app:bootRun --args='--logging.level.org.springframework.boot.context.config=TRACE'
```

Chỉ dùng lệnh trên nếu `bootRun` nhận được `--args` qua Gradle setup hiện tại. Có thể thay bằng `--logging.level.org.springframework.boot.context.config=TRACE` khi chạy executable jar. Hạn chế chia sẻ log cấu hình vì có thể lộ đường dẫn, endpoint, metadata nhạy cảm.

## 10. Checklist trước khi merge / deploy

- [ ] `application.yml` vẫn chỉ khai báo profile selection.
- [ ] Mỗi environment có đầy đủ cấu hình mà code của environment đó cần; không dựa vào một `application-common.yml` không tồn tại.
- [ ] `setup` / `migrate` không chứa URL/password/token của một môi trường cụ thể.
- [ ] Không có production credentials, token hoặc secret thật trong Git.
- [ ] Đã kiểm tra các giá trị `${VAR}` (required) và `${VAR:}` (default rỗng).
- [ ] Đã xác nhận transport (`inmemory` hay `kafka`) và rate-limit store (`memory` hay `redis`) cho production.
- [ ] Đã kiểm tra `dev`, `test`, `prod` và các tổ hợp operational profile liên quan.
- [ ] Nếu thêm key `trellis.*`, đã cập nhật binding/validation và `SensitiveProperties` nếu cần.
- [ ] Với migration/setup production, đã có backup, review, permissions và runbook.

## 11. Nguồn tham khảo

- [Spring Boot — Profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html)
- [Spring Boot — Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- Bộ file Trellis: `config/*.yml`, `application.yml`, `application-dev.yml`, `application-test.yml`, `application-prod.yml`, `application-setup.yml`, `application-migrate.yml` (cùng thư mục với hướng dẫn này khi phân phối).

> **Tình trạng xác minh:** Tài liệu được biên soạn từ đúng các file YAML đã cung cấp. Các lệnh Gradle/JAR/Compose là hướng dẫn sử dụng theo cấu trúc dự án; chưa kiểm tra trực tiếp Gradle task, Docker Compose và integration test trên toàn bộ repository.
