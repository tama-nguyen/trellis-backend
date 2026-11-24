# Dùng `trellis-common`

`trellis-common` là module nền, **không phụ thuộc Spring, không phụ thuộc framework nào**. Mọi module
khác đều phụ thuộc nó, và nó không phụ thuộc ai cả. Đây là nơi đặt những thứ mà cả backend lẫn một
test thuần Java đều dùng được.

Vì nó nằm dưới cùng, thêm một thứ vào đây là **quyết định có ảnh hưởng tới mọi module**. Mục 4 nói
khi nào nên và khi nào không nên.

---

## 1. Có gì trong đó

| Package | Chứa gì | Dùng khi |
| --- | --- | --- |
| `aop` | 8 annotation cắt ngang (mục 2) | đánh dấu một use case cần audit, quyền, idempotency… |
| `arch` | `ArchExempt` | miễn trừ một rule ArchUnit, **bắt buộc ghi lý do** |
| `context` | `RequestContext`, `Actor`, `ActorType`, `TraceContextAccessor`, `AmbientRequestContext` | biết ai đang gọi, correlation id, thời điểm request |
| `error` | `TrellisException` và 7 nhánh con, `ErrorCode`, `ErrorCategory`, `ProblemFields`, `ProblemTypes`, `RetryAfterAware` | ném lỗi nghiệp vụ; tầng web tự đổi sang RFC 7807 |
| `http` | `HttpHeaderNames` | tên header, không gõ chuỗi |
| `id` | `UuidV7`, `ReferenceCode`, `ReferenceCodeGenerator`, `StableId` | sinh khoá chính, mã người đọc được, id ổn định từ nội dung |
| `json` | `CanonicalJson` | JSON chuẩn hoá để băm; thứ tự key cố định |
| `logging` | `LogKeys` | tên field trong log và MDC |
| `page` | `Cursor`, `CursorCodec`, `CursorPage`, `OffsetPage` | phân trang; mặc định dùng cursor |
| `retry` | `Backoff` | thử lại có jitter; **cùng công thức với Python** |
| `security` | `Permission`, `PermissionCatalog`, `PermissionRegistry`, `PermissionRequirement`, `AuthorizationService`, `AuthorizationDecision`, `PlatformPermissions` | khai báo và kiểm tra quyền |
| `validation` | `CommandValidator`, `ValidationPipeline`, `Violation`, `Violations` | kiểm tra đầu vào, gom hết lỗi trả một lần |

Hai thứ đáng chú ý vì chúng là **hợp đồng xuyên ngôn ngữ**, không chỉ là tiện ích:

- `retry/Backoff` và `id/StableId` phải cho ra kết quả giống hệt bản Python trong `trellis-ai`. Cả hai
  bên đọc chung bộ vector ở `contracts/testdata/backoff.json` và `stable-id.json`. Sửa công thức ở
  một bên mà quên bên kia thì test chung sẽ đỏ, đó là chủ ý.
- `error/ProblemTypes` quyết định chuỗi `type` trong body lỗi mà frontend đọc để biết có hiển thị
  message của server hay không.

---

## 2. Custom annotation

Tám annotation nằm ở `io.trellis.common.aop`. **Chúng chỉ là khai báo**: phần thực thi là các aspect
trong `trellis-platform`. Tách như vậy để module nghiệp vụ dùng annotation mà không kéo theo phần
cài đặt.

| Annotation | Tham số | Làm gì |
| --- | --- | --- |
| `@RequiresPermission` | `anyOf`, `allOf` | chặn nếu người gọi thiếu quyền; trả 403 |
| `@Audited` | `action`, `aggregateType`, `aggregateId` (SpEL) | ghi một dòng audit **trong cùng transaction** |
| `@Idempotent` | `key` (SpEL), `ttl` mặc định `PT24H` | gọi lại cùng key thì trả kết quả đã lưu, không chạy lại |
| `@RateLimited` | `key` (SpEL), `permits`, `per` | giới hạn tần suất; vượt thì 429 |
| `@DistributedLock` | `key` (SpEL), `waitFor`, `leaseFor` | khoá advisory của Postgres, chống hai instance chạy cùng lúc |
| `@LogExecution` | `level`, `logArgs` mặc định `false` | log thời gian chạy; **không log tham số** trừ khi bật |
| `@RequiresFeature` | `flag`, `fallback` | tắt một thao tác theo feature flag |
| `@Sensitive` | — | đánh dấu **tham số**, để `@LogExecution` che giá trị |

Metrics và tracing **không** có annotation riêng: dùng `@Observed` của Micrometer.

### Thứ tự chạy

Thứ tự quyết định đúng sai, không phải chuyện thẩm mỹ. Khai báo tại `AspectOrder` trong
`trellis-platform`, từ ngoài vào trong:

```text
@LogExecution → @RequiresFeature → @RateLimited → @RequiresPermission
              → @Idempotent → @DistributedLock → @Transactional → @Audited
```

Đọc từ trên xuống thì logic rõ: đếm rate trước khi kiểm quyền (người không có quyền vẫn không được
spam), kiểm quyền trước khi tra idempotency (không cho người lạ dò key), và `@Audited` nằm **trong**
transaction nên dòng audit và dữ liệu cùng commit hoặc cùng rollback.

### Ba cái bẫy

1. **Gọi nội bộ qua `this` thì aspect không chạy.** Spring chạy aspect qua proxy; `this.publish()`
   bỏ qua proxy. Method có annotation phải được gọi từ bên ngoài bean.
2. **Method phải `public`** và thuộc một Spring bean.
3. **Key SpEL sai chính tả không báo lỗi lúc build.** `@Idempotent(key = "#cmd.coed")` vẫn chạy, chỉ
   là mỗi lần ra một key khác, nên idempotency im lặng mất tác dụng. Viết test cho đường gọi lặp.

---

## 3. Utils: ở đâu và khi nào dùng

"Utils" trong `trellis-common` **không** có package tên `util`. Đó là cố ý: một package tên `util` sẽ
thành nơi chứa mọi thứ không biết để đâu. Mỗi nhóm tiện ích nằm trong package mang tên **chủ đề**.

Khi cần một tiện ích, tìm theo chủ đề:

| Cần | Dùng | Đừng tự viết |
| --- | --- | --- |
| Khoá chính mới | `UuidV7.generate()` | `UUID.randomUUID()` — mất tính sắp xếp theo thời gian, làm phân mảnh index |
| Mã cho người đọc, gõ, đọc qua điện thoại | `ReferenceCodeGenerator` | chuỗi random tự chế; bộ chữ Crockford bỏ các ký tự dễ nhầm |
| Id ổn định suy ra từ nội dung | `StableId` | hash tự viết; bên Python phải ra cùng kết quả |
| Thử lại | `Backoff` | vòng `sleep` cố định; thiếu jitter thì mọi client dội lại cùng lúc |
| Phân trang | `CursorPage` + `CursorCodec` | `OFFSET` lớn; vừa chậm vừa lệch khi dữ liệu thay đổi |
| Kiểm tra đầu vào | `ValidationPipeline` | ném ở lỗi đầu tiên; người dùng phải sửa từng lỗi một |
| Tên header, tên field log | `HttpHeaderNames`, `LogKeys` | chuỗi viết thẳng |
| Báo lỗi nghiệp vụ | nhánh con của `TrellisException` | `RuntimeException` trơn; tầng web sẽ không map được sang mã HTTP đúng |

`OffsetPage` vẫn còn cho màn hình quản trị cần biết tổng số trang. Mặc định là `CursorPage`.

---

## 4. Thêm thứ mới: đặt ở đâu

Thứ tự câu hỏi, dừng ở câu đầu tiên trả lời "có":

1. **Chỉ một module dùng?** → để trong module đó. Một người dùng chưa phải bằng chứng là nó dùng chung.
2. **Hai module đã cần?** → lúc này mới nâng lên. Nâng kèm test chứng minh cả hai chạy được.
3. **Nó cần Spring, JDBC, HTTP hay Kafka?** → **không** thuộc `trellis-common`. Đưa vào
   `trellis-platform` (hoặc `-events`, `-web`). `trellis-common` không có dependency framework, và có
   rule ArchUnit giữ điều đó.
4. **Nó là annotation?** → khai báo ở `common/aop`, aspect thực thi ở `trellis-platform`, và thêm một
   hằng số vào `AspectOrder` kèm lý do vì sao nó đứng ở vị trí đó.
5. **Nó là tiện ích thuần?** → tạo package mới theo **chủ đề** (`time`, `text`, `money`), không nhét
   vào một package sẵn có cho tiện, và tuyệt đối không tạo `util`.

Package mới chỉ tồn tại khi có class trong đó; vai trò và phạm vi phụ thuộc do ArchUnit canh, không phải bằng file đánh dấu.

**Nhớ:** thêm vào `trellis-common` là thêm vào classpath của mọi module, mãi mãi. Xoá đi thì khó, vì
không biết ai đang dùng. Khi phân vân, để lại trong module và đợi người dùng thứ hai.

---

## 5. Liên quan

- Kiến trúc và danh mục pattern: [../architecture/backend.md](../architecture/backend.md)
- Quy tắc code Java, bảng AOP: [../../AGENTS.md](../../AGENTS.md)
- Thêm một module nghiệp vụ: [new-module.md](new-module.md)
