# Plan: rate limiting cho HTTP và native identity

Trạng thái: đề xuất để review, chưa triển khai. Ngày: 2026-11-24.
Baseline: `cc77c62` — cấu hình bằng Spring field `@Value`, Google Java Format.

## 1. Quyết định đề xuất

Giữ operation throttling trên application boundary qua `@RateLimited` + aspect; bổ sung HTTP ingress
limiting ở web/security boundary. Hai lớp dùng cùng engine/store nhưng có policy và bucket độc lập.

Annotation là metadata khai báo chính sách. `RateLimitedAspect` thực thi chính sách trước khi use case
chạy. Việc đặt annotation trên application service phù hợp với cơ chế AOP hiện tại; không có nguyên tắc
chung bắt buộc mọi rate limit đều nằm ở controller.

Không chuyển toàn bộ annotation sang endpoint: điều đó bỏ bảo vệ các lời gọi application service từ
GraphQL, job hoặc adapter khác. Cũng không đưa IP, servlet request hay Redis vào domain/use case.
Controller chỉ bind/validate DTO và gọi application service.

```text
Trusted ingress / BFF: connection, request-size, coarse source limits
  -> API security filter: HTTP bucket theo route policy + trusted client IP
  -> controller: bind + validate DTO
  -> application proxy: operation bucket theo canonical subject
  -> transaction -> use case -> database / mail
```

Thứ tự chi tiết của security filter phải giữ CORS/preflight, correlation ID và exception handling đúng.
Filter IP chạy trước bearer authentication tốn chi phí, không đọc body. Operation aspect tiếp tục chạy
trước transaction. Với protected operation, giữ kiểm tra authorization theo `AspectOrder` hiện hữu;
không cho người gọi tự chọn subject của một người khác.

Đây là lựa chọn kiến trúc cho Trellis dựa trên implementation và các nguồn ở cuối plan, không phải một
vị trí layer duy nhất do Spring hoặc OWASP bắt buộc.

## 2. Những gì đã xác minh trong code

| Hiện trạng | Hệ quả |
| --- | --- |
| `RateLimitedAspect.limit`: `@Around("@annotation(limited)")` | Bỏ annotation đồng nghĩa bỏ operation limit; aspect không tự bảo vệ mọi endpoint. |
| `ArchitectureTest.aopAnnotationsOnlyOnApplicationServices` | Convention hiện tại chủ động đặt AOP trên application service. |
| `AspectOrder.RATE_LIMITED` đứng trước `TRANSACTION` | Request bị chặn trước khi mở transaction. |
| Login/register/forgot/reset dùng `key = "#email"` | Email chưa normalize khi tính bucket; thay case/khoảng trắng có thể tạo bucket khác. |
| Key là `SimpleClass.method + ':' + subject` | Refactor tên class có thể reset quota; tên class trùng có thể chia sẻ bucket ngoài ý muốn. |
| Redis adapter nối key trực tiếp | Email có thể xuất hiện trong Redis key; hash token riêng không giải quyết việc này. |
| API chain chưa cài limiter IP | Chưa có backend ingress limit độc lập với operation limit. Không suy ra từ đó rằng gateway bên ngoài cũng chưa giới hạn. |
| `application-prod.yml` mặc định `store=memory` | N instance có N bộ quota; restart hoặc eviction có thể làm đầy lại bucket. |
| `LocalRateLimiterStore` expire sau 1h idle | Policy có thời gian hồi đầy >1h có thể bị reset sớm. |
| Bucket configuration được cấp khi tạo bucket | Đổi cấu hình không có nghĩa bucket đang tồn tại tự cập nhật; cần cơ chế migration rõ ràng. |
| Store chỉ trả `Duration`, không phân biệt backend lỗi | Cần quy định riêng cho quota exceeded và Redis unavailable. |

Các nhận định này là kết quả đọc source tại baseline, chưa phải kết quả load test hay kiểm chứng môi
trường production.

## 3. Ownership và contract

| Nơi | Trách nhiệm sau thay đổi |
| --- | --- |
| `trellis-common` | Annotation và kiểu quyết định/error dùng chung, không Spring hay servlet. |
| `trellis-platform` | Policy registry, key encoding, rate-limit engine, Bucket4j adapters, operation aspect. |
| `trellis-platform-web` | HTTP route matching, trusted client address, ingress filter, response writer. |
| `trellis-identity` | Khai báo policy ID cho auth và resolver canonical email theo `EmailAddress.of`. |
| `trellis-app` | Giá trị cấu hình theo environment, secret, wiring, classification và rollout. |
| Ingress/BFF | Chặn traffic sớm và truyền client address qua trust boundary đã cấu hình. |

Các type dự kiến:

- `RateLimitPolicy`: ID ổn định, bandwidth/burst, subject resolver và chế độ lỗi backend.
- `RateLimitPolicyRegistry`: đăng ký policy, từ chối ID trùng hoặc thiếu.
- `RateLimitDecision`: allowed / exceeded cùng retry delay; lỗi store là lỗi riêng.
- `RateLimitKeyEncoder`: HMAC subject, namespace theo app/environment, policy ID và revision.
- `OperationRateLimitSubjectResolver`: seam trong platform để identity đăng ký canonical email resolver.
- `ClientAddressResolver` và `HttpRateLimitFilter`: chỉ thuộc web.

Không xây DSL hoặc framework policy động. Phase đầu chỉ cần immutable policies nạp từ field `@Value`,
đăng ký khi khởi động. Giữ interface store nhỏ; chỉ mở rộng khi bandwidth/revision thật sự cần.

Annotation đích có dạng:

```java
@RateLimited(policy = IdentityRateLimitPolicies.REQUEST_PASSWORD_RESET, key = "#email")
```

`IdentityRateLimitPolicies` sở hữu constant ID; policy chọn email resolver. SpEL chỉ trích subject,
không tự normalize email và không gọi Redis. `permits`, refill và burst chuyển sang cấu hình có tên rõ.
Các annotation cũ ở notice/course phải được migrate trong cùng đợt, không để hai nguồn threshold cho cùng
policy. Không tự đổi `RequiresPermission`, `Transactional` hoặc các annotation khác.

## 4. Bucket và response theo luồng

IP bucket và account bucket phải độc lập: một IP thử nhiều email vẫn tốn cùng IP quota; nhiều IP thử
một email vẫn tốn cùng account quota. Chỉ dùng cặp `IP + email` không đáp ứng cả hai mục tiêu.

| Luồng | HTTP subject | Operation subject | Khi operation quota hết |
| --- | --- | --- | --- |
| Login | IP / nhóm route auth | Canonical email, kể cả email không tồn tại | 429 + Retry-After, độc lập user tồn tại hay không. |
| Register | IP / nhóm route auth | Canonical email | 429 trước hash password và persistence. |
| Forgot password | IP / nhóm route recovery | Canonical email, kể cả email không tồn tại | 202 chung; bỏ qua tạo code và gửi mail. |
| Reset password | IP / nhóm route recovery | Canonical email | 429, không đổi password hoặc consume action. |
| Verify email | IP / nhóm route action | HMAC token đầy đủ cho quota phụ, tuyệt đối không token prefix chung | 429; IP quota vẫn cần để chặn random token. |
| Refresh/logout | IP / nhóm route session | HMAC refresh secret cho quota phụ | 429 trước DB lookup; quota đủ rộng cho retry hợp lệ. |
| Protected API | IP trước authentication | Actor ID đã xác thực nếu operation cần | 429 theo policy của operation. |

HTTP IP quota hết luôn trả 429. Không trả 202 cho mọi failure: 202 ở forgot-password chỉ áp dụng kết quả
nghiệp vụ chung và account suppression, không che lỗi hạ tầng dùng chung.

Để xử lý suppression mà không làm generic aspect biết identity:

1. Với policy thông thường, aspect biến `Exceeded` thành `RateLimitedException`.
2. Với forgot-password, dùng một application entrypoint `RequestPasswordResetUseCase` không trả value;
   entrypoint gọi operation được proxy riêng và bắt **chỉ** `RateLimitedException` của chính operation đó.
   Controller vẫn trả 202. Không dùng self-invocation để kích hoạt aspect.
3. Không nuốt lỗi Redis, database, validation hoặc transaction trong nhánh suppression.

Nếu cách entrypoint này làm dư class đáng kể, lựa chọn thay thế phải được ghi trong ADR trước khi code:
operation policy qua application guard/port gọi tường minh. Không dùng aspect trả `null` tùy ý để giả
thành công cho mọi return type.

Quota tính tất cả attempt, không chỉ failure. Không hoàn token sau transaction rollback hoặc sau login
thành công; tránh concurrency khiến bucket hồi đầy ngoài dự kiến. Không khóa/vô hiệu hóa tài khoản khi
hết quota. Account throttling vẫn có thể bị lợi dụng gây gián đoạn đăng nhập; dùng cooldown ngắn, quan
sát thực tế và chỉ thêm CAPTCHA/step-up khi cần. Không hứa rate limiting loại bỏ được rủi ro này.

## 5. Key, privacy và trust boundary

- Email: normalize đúng `EmailAddress.of`, không tự thêm luật xóa dấu chấm hoặc alias `+`.
- Tạo account bucket trước lookup user, cùng thuật toán cho account tồn tại, không tồn tại và disabled.
- Dùng `HMAC-SHA-256(secret, subjectType + separator + canonicalSubject)`; không dùng SHA-256 email trần
  vì email dễ dò từ dictionary. Secret riêng cho rate-limit, dùng chung giữa các instance.
- Không đưa email/IP/raw access, refresh hoặc action token vào Redis key, log hay metric label.
  HMAC là pseudonymization, vẫn cần TTL và kiểm soát truy cập Redis.
- Policy ID phải ổn định, không lấy tên class/method hay URL chứa ID. Unknown policy hoặc subject lỗi
  phải báo lỗi; không dồn mọi request lỗi vào bucket chuỗi `null`.
- Không đọc trực tiếp `X-Forwarded-For` đầu tiên từ Internet. Proxy biên phải strip/overwrite header
  người dùng gửi; backend chỉ tin proxy đã allowlist. Nếu không có proxy tin cậy thì dùng peer address.
- Với BFF, phải truyền client address được xác thực trong topology đã cấu hình; dùng IP BFF cho mọi
  người sẽ làm họ chia sẻ một bucket. Chặn truy cập origin ngoài tuyến ingress được tin cậy.
- IPv6 phải canonicalize; gộp subnet là policy riêng cần đo NAT/mobile thực tế, không mặc định gộp /64
  cho mọi môi trường.

## 6. Store, cấu hình và vận hành

Giữ Bucket4j token bucket với greedy refill. `permits/per` hiện là tốc độ hồi token, không phải số request
tối đa trong một fixed window. Khi cần kiểm soát burst và sustained rate, dùng hai bandwidth trong
cùng bucket để việc consume là atomic; không biến hai bandwidth thành hai bộ counter rời.

Các quota IP/account là counter riêng: request chỉ đi tiếp nếu cả hai cho phép. Không yêu cầu atomic
transaction xuyên các bucket; nếu IP quota đã consume nhưng account quota từ chối thì không hoàn IP
token. Cách này giới hạn bảo thủ và không mở đường refund race.

Giá trị khởi điểm để đo, không phải ngưỡng do OWASP bảo đảm an toàn:

| Policy | Burst/refill đề xuất ban đầu |
| --- | --- |
| Auth HTTP / IP | 60 token, hồi 60/phút; đo lại cho môi trường NAT/BFF. |
| Recovery HTTP / IP | 20 token, hồi 20/phút. |
| Login / account | 5 token, hồi 5/phút. |
| Register / email | 3 token, hồi 3/15 phút. |
| Forgot / email | 1 token, hồi 1/phút; thêm bandwidth 3 token, hồi 3/giờ. |
| Reset / email | 5 token, hồi 5/15 phút. |

Ngưỡng verify/refresh/logout phải được chọn sau khi đo concurrency/retry client; không ép threshold login
cho tất cả route. App/group-wide circuit capacity ở ingress là kiểm soát riêng để bảo vệ BCrypt/SMTP
khi attacker đổi cả IP lẫn email.

Cấu hình mới đi theo convention vừa chốt: field `@Value`, key/comment trong từng profile, classification
trong `SensitiveProperties`; secret từ environment/secret store. Prod dùng Redis và từ chối fallback
âm thầm sang memory. Dev/test được dùng memory rõ ràng.

- TTL không được ngắn hơn thời gian bucket hồi đầy; local cache phải xét policy này thay vì idle 1h cố định.
  Caffeine eviction vẫn có thể reset quota; chỉ xem memory store là lựa chọn dev/single-process.
- Cấu hình policy thay đổi: phase đầu dùng revision có chủ đích trong namespace. Revision bump tạo quota
  mới nên cần rollout đồng bộ; giữ revision cũ đến TTL. Không tạo revision mới ngẫu nhiên mỗi startup.
  Nếu cần giữ quota xuyên thay đổi thì triển khai Bucket4j `replaceConfiguration` với bandwidth ID và
  token inheritance được test; không dựa vào supplier lúc tạo bucket để cập nhật bucket đã tồn tại.
- Redis lỗi/timeout: native auth mutation và token exchange trả 503 với thông báo chung; không giả 429,
  không tự mở limiter. Có timeout và circuit breaker ngắn để tránh làm nghẽn request pool.
- Không áp dụng failure policy này cho management health/readiness của limiter; vận hành vẫn phải quan sát
  được Redis hỏng. Protected read-only endpoint có thể chọn degrade policy riêng sau này.
- HTTP filter dùng `ProblemResponseWriter`; aspect dùng exception mapping hiện có. Retry-After làm tròn
  lên giây, tối thiểu 1 khi blocked; không lộ policy bucket hoặc email trong response.
- Metrics chỉ gắn `policy`, `layer`, `outcome`, `store`; theo dõi suppression và store errors riêng, không
  subject label. Thêm Redis cardinality/latency và tỷ lệ 429/503 trước rollout.

## 7. Kế hoạch triển khai theo commit

1. **ADR 0005 + contracts**: chốt hai boundary, semantics forgot 202/429/503, key trust/privacy,
   failure mode và quota model. Đây mới là tài liệu đề xuất; chưa đánh dấu ADR accepted.
2. **Engine + policy registry**: stable ID, canonical subject resolver, HMAC encoder, decisions và
   bandwidth; giữ local/Redis adapter, thêm test atomic consume và TTL.
3. **Operation aspect + identity policies**: migrate auth/notice/course, bỏ threshold literal khỏi
   use case; giữ aspect order trước transaction; triển khai forgot suppression qua proxy boundary rõ.
4. **HTTP ingress**: filter trong API security chain, route groups, trusted IP resolver, Problem writer;
   không auto-register filter thêm một lần qua servlet container.
5. **Prod wiring**: Redis required, key secret, policy revisions, startup diagnostics, classification,
   metrics và runbook. Compose dev thêm profile Redis cho integration checks.
6. **Docs/contracts**: cập nhật ADR 0003, auth docs và JUnit plan; regenerate OpenAPI bằng task repo nếu
   response contract thay đổi, ghi breaking changes khi đổi annotation/config/API.

Không chỉnh domain password policy, thiết kế OTP hay SMTP transport trong cùng đợt trừ khi phát hiện
bug trực tiếp cản việc triển khai rate limiting.

## 8. Test và tiêu chí hoàn thành

- Unit: canonical email variants chung bucket; stable policy ID; keys không chứa PII/token; null/malformed
  subject không bypass; retry delay rounding; greedy refill và hai bandwidth dùng clock điều khiển được.
- Platform AOP integration: quota hết không mở transaction, không gọi BCrypt/DB/mail; exception/rollback
  không refund token; chứng minh dùng proxy thật và không self-invocation.
- Identity integration: forgot known/unknown/disabled cùng status và quota progression; account suppression
  không issue code/mail, không revoke code cũ; Redis lỗi trả 503 chung và không gửi mail.
- HTTP: CORS preflight, malformed payload, endpoint group matching, filter chạy đúng một lần, management
  không bị ảnh hưởng; spoofed forwarded headers không tạo IP mới; BFF trusted forwarding hoạt động.
- Abuse matrix: một IP/nhiều account; nhiều IP/một account; khác case/whitespace; thay token ngẫu nhiên;
  actor anonymous không bị dồn vào một global bucket; token quota chỉ là kiểm soát phụ cho IP quota.
- Redis Testcontainers: hai application context chia quota, concurrency không vượt số token ban đầu,
  instance restart giữ quota, TTL đúng, backend outage/recovery và revision rollout đúng semantics.
- API responses: 429/Retry-After thống nhất giữa filter/aspect; 503 khác 429; không có PII trong problem,
  Redis key hay labels. Forgot timing phải được đo vì synchronous SMTP hiện tại vẫn có chênh lệch; nếu
  cần uniform timing thì tách kế hoạch async/outbox mail, không thêm `sleep` để che chênh lệch.
- `./gradlew build` bằng JDK 21, gồm format, architecture, classification, context load và API contract
  checks. Test backend multi-instance và metrics là điều kiện rollout production.

Owner đã yêu cầu JUnit cho use cases và plan này bao gồm test bảo mật nền tảng; không test chỉ để kiểm
tra annotation tồn tại. Các số quota phải được xác nhận lại bằng traffic thực tế trước khi enforce rộng.

## 9. Nguồn đối chiếu

- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html):
  account-oriented throttling và tránh lockout bị lợi dụng.
- [OWASP Bot Management and Anti-Automation](https://cheatsheetseries.owasp.org/cheatsheets/Bot_Management_and_Anti-Automation_Cheat_Sheet.html):
  IP bucket và username bucket độc lập; không chỉ ghép IP + username.
- [OWASP Forgot Password Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html):
  chống flooding và không lộ account existence qua recovery response.
- [Spring AOP proxying](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html):
  semantics proxy và giới hạn self-invocation.
- [Spring forwarded-header security](https://docs.spring.io/spring-framework/reference/web/webmvc/filters.html):
  trust boundary đối với forwarded headers.
- [Bucket4j reference](https://bucket4j.com/8.16.0/toc.html): token bucket, distributed state và configuration
  replacement. Repo pin `8.22.0`; API cụ thể phải đối chiếu artifact/Javadoc đúng version khi triển khai.

Nguồn không quy định các con số quota và không bắt buộc annotation nằm trên controller. Các threshold,
type/class dự kiến và thứ tự commit ở trên là đề xuất riêng cho repository này.
