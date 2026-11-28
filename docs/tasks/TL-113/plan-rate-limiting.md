# Plan: rate limiting cho HTTP và native identity

Jira: [TL-113](https://tama-nguyen.atlassian.net/browse/TL-113)

Trạng thái: đã triển khai trong working tree; kiểm chứng topology production theo runbook trước rollout. Ngày: 2026-11-24.
Baseline: `cc77c62` — cấu hình bằng Spring field `@Value`, Google Java Format.

## 1. Quyết định đề xuất

Phase 1 giới hạn theo IP tại HTTP filter cho đúng bảy route POST của native identity ở mục 4.
Filter chạy sau CORS và correlation ID, trước bearer authentication. Filter khớp method và path
bằng request matcher của security chain, bỏ qua preflight và chỉ lấy địa chỉ client từ nguồn tin cậy.

Quota theo email hoặc actor được khai báo bằng `@RateLimited` trên public controller methods.
Aspect chạy sau DTO validation và trước lời gọi use case, bao gồm transaction của use case.
Login, register, forgot/reset password và create notification/course đều theo quy tắc này.
Use case chỉ sở hữu nghiệp vụ, authorization và transaction. Adapter khác tự sở hữu admission control.
ArchitectureTest kiểm tra RateLimited thuộc web, các business AOP annotation khác thuộc application.

## 2. Những gì đã xác minh trong code

| Hiện trạng | Hệ quả |
| --- | --- |
| `RateLimitedAspect.limit`: `@Around("@annotation(limited)")` | Bỏ annotation đồng nghĩa bỏ operation limit; aspect không tự bảo vệ mọi endpoint. |
| Rule trước thay đổi gộp mọi AOP vào application | Tách rule riêng cho RateLimited trên controller theo quyết định của owner. |
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
@RateLimited(policy = IdentityRateLimitPolicies.LOGIN, key = "#request.email")
```

`IdentityRateLimitPolicies` sở hữu constant ID; policy chọn email resolver. SpEL chỉ trích subject,
không tự normalize email và không gọi Redis. `permits`, refill và burst chuyển sang cấu hình có tên rõ.
Các annotation cũ ở notification/course phải được migrate trong cùng đợt, không để hai nguồn threshold cho cùng
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
| Verify email | IP / nhóm route action | — | Chỉ áp dụng quota IP. |
| Refresh và logout | IP / nhóm route session | — | Chỉ áp dụng quota IP. |
| Protected API | Ngoài phạm vi filter phase 1 | Actor của operation đã khai báo policy (notification/course) | 429 cho operation đó. |

Danh sách matcher dưới prefix `/api/auth` gồm POST `/login` và `/register` (auth),
`/forgot-password` và `/reset-password` (recovery), `/verify-email` (action), `/refresh` và
`/logout` (session). Các request khác đi tiếp qua security chain hiện tại. Hết quota IP trả
429 cùng Retry-After trước khi controller đọc body.

Refresh thành công sẽ rotate token. Tạo bucket theo token vừa cấp sẽ cấp lại quota cho cùng session,
còn mỗi token rác có thể sinh thêm một Redis key. Phase 1 chỉ tạo bucket IP cho ba route verify,
refresh và logout. Nếu cần quota theo session trong phase sau, dùng ID của session đã xác thực,
kiểm tra sau lookup và trước rotate.

**Luồng forgot-password**

1. `AuthController.forgotPassword` khai báo `@RateLimited` với policy FORGOT,
   key `#request.email` và `onExceeded = SKIP_VOID`. Aspect chuẩn hóa email qua policy resolver và consume
   quota trước lookup và transaction.
2. Khi hết quota, aspect hoàn tất lời gọi void mà không chạy use case. Controller trả 202.
   Hành vi này áp dụng giống nhau cho email known, unknown và user disabled.
3. Redis lỗi đi theo error mapping 503. Khi được phép, aspect gọi tiếp transaction và use case
   cấp mã/gửi email. Business logic chỉ sở hữu luồng nghiệp vụ.
4. SKIP_VOID chỉ hợp lệ với method trả void. Startup verifier và aspect đều kiểm tra điều kiện này.
   Policy mặc định REJECT tiếp tục ném RateLimitedException khi hết quota.

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

Ngưỡng IP của nhóm action và session phải được chọn sau khi đo concurrency/retry client; không ép threshold login
cho tất cả route. App/group-wide circuit capacity ở ingress là kiểm soát riêng để bảo vệ BCrypt/SMTP
khi attacker đổi cả IP lẫn email.

Cấu hình mới đi theo convention vừa chốt: field `@Value`, key/comment trong từng profile, classification
trong `SensitiveProperties`; secret từ environment/secret store. Prod dùng Redis và từ chối fallback
âm thầm sang memory. Dev/test được dùng memory rõ ràng.

- TTL không được ngắn hơn thời gian bucket hồi đầy; local cache phải xét policy này thay vì idle 1h cố định.
  Caffeine eviction vẫn có thể reset quota; chỉ xem memory store là lựa chọn dev/single-process.
- Rolling deploy thông thường giữ nguyên policy, revision và HMAC secret trên mọi instance phục vụ
  traffic. Rollback binary cũng dùng cùng cấu hình. Secret lấy theo version cố định từ secret store.
- Khi cần đổi namespace, HMAC secret hoặc cấu hình bandwidth, phase 1 chọn cutover có bảo trì.
  Bước 1 triển khai binary tương thích với cấu hình đang active. Bước 2 tạm ngừng tiếp nhận mọi
  lời gọi tới policy bị ảnh hưởng ở tất cả adapter, drain request đang chạy và ghi nhận lần consume cuối.
  Chờ đủ thời gian hồi đầy dài nhất của các bandwidth cũ trước khi chuyển toàn bộ instance sang cấu hình
  mới. Kiểm tra version cấu hình trên mọi instance rồi mới mở traffic. Khoảng chờ tính từ lúc drain
  xong. Cách này chấp nhận gián đoạn để tránh cấp lại quota sớm. Lần chuyển đầu từ key cũ hoặc
  memory sang Redis cũng áp dụng quy trình này cho các quota đang có.
- Nếu rollback trước khi mở traffic, có thể quay lại toàn bộ cấu hình cũ. Sau khi đã mở traffic,
  ưu tiên rollback binary tương thích với cấu hình active. Nếu phải đổi lại namespace hoặc secret,
  lặp lại cutover với thời gian hồi đầy của policy đang phục vụ. Runbook phải ghi thời gian bảo trì
  dự kiến và phiên bản cấu hình có thể rollback.
- Khi Redis lỗi hoặc timeout, route trong danh sách matcher trả 503 và dừng trước side effect.
  Operation notification/course dùng Redis cũng trả 503 nếu bước kiểm tra quota lỗi. Dùng timeout hữu hạn
  và circuit breaker để giới hạn thời gian chờ. Management và `GET /api/auth/me` đi qua đường xử lý
  hiện tại, kể cả khi xác thực bằng Keycloak JWT.

- HTTP filter dùng `ProblemResponseWriter`; aspect dùng exception mapping hiện có. Retry-After làm tròn
  lên giây, tối thiểu 1 khi blocked; không lộ policy bucket hoặc email trong response.
- Metrics chỉ gắn `policy`, `layer`, `outcome`, `store`; theo dõi suppression và store errors riêng, không
  subject label. Thêm Redis cardinality/latency và tỷ lệ 429/503 trước rollout.

## 7. Kế hoạch triển khai theo commit

1. **ADR 0005 + contracts**: chốt hai boundary, semantics forgot 202/429/503, key trust/privacy,
   failure mode và quota model. Quyết định được ghi trong ADR 0005.
2. **Engine + policy registry**: stable ID, canonical subject resolver, HMAC encoder, decisions và
   bandwidth; giữ local/Redis adapter, thêm test atomic consume và TTL.
3. **Operation aspect + identity policies**: migrate auth/notification/course, bỏ threshold literal khỏi
   controller; business use cases không khai báo rate limiting; khai báo SKIP_VOID trên forgot-password controller và kiểm tra contract void.
4. **HTTP ingress**: filter trong API security chain, bảy POST matcher ở mục 4, trusted IP resolver, Problem writer;
   không auto-register filter thêm một lần qua servlet container.
5. **Prod wiring**: Redis required, key secret, policy revisions, startup diagnostics, classification,
   metrics và runbook. Dùng Redis trong profile Compose full hiện có cho integration checks.
6. **Docs/contracts**: cập nhật ADR 0003, auth docs và JUnit plan; regenerate OpenAPI bằng task repo nếu
   response contract thay đổi, ghi breaking changes khi đổi annotation/config/API.

Không chỉnh domain password policy, thiết kế OTP hay SMTP transport trong cùng đợt trừ khi phát hiện
bug trực tiếp cản việc triển khai rate limiting.

## 8. Test và tiêu chí hoàn thành

- Unit: canonical email variants chung bucket; stable policy ID; keys không chứa PII/token; null/malformed
  subject không bypass; retry delay rounding; greedy refill và hai bandwidth dùng clock điều khiển được.
- Platform AOP integration: quota hết không mở transaction, không gọi BCrypt/DB/mail; exception/rollback
  không refund token; chứng minh dùng proxy thật và không self-invocation.
- Identity integration dùng Spring proxy thật và theo dõi transaction. Với email known/unknown/disabled,
  kiểm tra cùng quota progression và HTTP 202. Khi aspect chặn hoặc store lỗi, số transaction mới,
  lookup, mã tạo và mail gửi đều bằng 0. Mã đang dùng vẫn hợp lệ. Khi Allowed, xác minh lookup và
  ghi mã nằm trong transaction. Lỗi DB sau Allowed phải đi qua error mapping hiện hành.
- HTTP integration kiểm tra đủ bảy matcher, quota dùng chung trong từng nhóm, sai method/path,
  preflight, malformed body, filter chạy đúng một lần và trusted forwarding qua BFF. Header giả từ
  peer ngoài allowlist không thay đổi bucket. Với Redis outage, bảy route trả 503 còn
  `GET /api/auth/me` với JWT hợp lệ vẫn thành công và management vẫn truy cập theo quyền hiện có.
- Abuse matrix gồm một IP/nhiều email, nhiều IP/một email, email khác case/khoảng trắng.
  Gửi nhiều token khác nhau từ cùng IP vào verify/refresh/logout và kiểm tra chỉ có key IP theo
  nhóm route. Kiểm tra refresh thành công rồi dùng token mới vẫn tiêu cùng quota IP.
- Redis Testcontainers dùng hai instance và clock/thời gian test phù hợp để kiểm tra concurrent
  consume, restart, TTL và phục hồi sau outage. Làm cạn quota forgot-password, chạy đồng thời
  binary cũ/mới với cùng revision và secret rồi rollback một instance. Cả hai phải tiếp tục
  suppress và số mail gửi thêm bằng 0 trước khi quota hồi. Diễn tập cutover và rollback sau khi
  mở traffic cho cả thay revision lẫn rotate secret, xác minh không có thời điểm hai namespace
  cùng nhận request và không mở namespace mới trước khi quota cũ có thể hồi đầy.

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

## 10. Kết quả triển khai

Engine dùng policy registry, HMAC subject, nhiều bandwidth atomic và circuit khi store lỗi.
Forgot-password dùng annotation SKIP_VOID trên controller, quota thực thi bởi aspect trước khi gọi use case.
Bảy POST route có HTTP filter riêng trong security chain. Prod yêu cầu Redis, secret và namespace.
Action/session IP khởi điểm lần lượt 60 và 120/phút, cần đo trước rollout.
RateLimitDecision được biểu diễn bằng Duration.ZERO hoặc retry delay tại store/engine; lỗi backend
dùng ServiceUnavailableException. Aspect dùng SKIP_VOID để hoàn tất controller recovery khi quota hết.
Local store giữ cache giới hạn kích thước, bỏ idle TTL cố định để tránh reset policy dài sớm.

ADR 0005 và docs/guide/rate-limiting.md ghi cấu hình, trusted proxy và cutover.
Test tự động bao gồm canonical/private keys, circuit, invalid subject, concurrent multi-bandwidth,
shared Redis giữa hai adapter và restart, route scope, spoofed headers và transaction recovery.
Các bước đo tải, diễn tập mixed-version cutover và BFF topology thực tế thuộc kiểm chứng deployment.

Controller aspect và HTTP filter được kiểm tra qua HTTP thật trong ForgotPasswordRateLimitTest.
Các lời gọi use case trực tiếp không áp dụng quota HTTP.
