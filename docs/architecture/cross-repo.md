# Ba repo nói chuyện với nhau thế nào

Trellis là ba repo (quyết định T21). Tài liệu này nói chúng trao đổi qua những đường nào, đường nào
được phép, và điều gì giữ cho chúng không lệch nhau.

| Repo | Chạy gì | Sở hữu |
| --- | --- | --- |
| `trellis-backend` | Java, Spring Boot | database, luật nghiệp vụ, `contracts/`, stack local |
| `trellis-web` | Next.js | giao diện, và là BFF của trình duyệt |
| `trellis-ai` | Python | worker Kafka, RAG, SDK, CLI |

---

## 1. Bốn đường, không có đường thứ năm

```text
   trình duyệt
        │  chỉ gọi /api/* cùng origin, không bao giờ gọi thẳng backend
        ▼
   ┌──────────┐   ①  REST + GraphQL (đồng bộ, có token)   ┌──────────────┐
   │   web    │ ───────────────────────────────────────▶  │   backend    │
   │  (BFF)   │                                           │              │
   └──────────┘                                           │  PostgreSQL  │
                                                          │   + outbox   │
   ┌──────────┐   ②  REST qua SDK (đồng bộ)               └──────┬───────┘
   │    ai    │ ───────────────────────────────────────▶         │
   │ (worker) │                                                  │ ③ event
   │          │ ◀────────────────────────────────────────────────┘
   └──────────┘      Kafka: backend phát, ai tiêu thụ
        ▲
        │  ④ contract package (npm, PyPI) — sinh từ contracts/ của backend
        └──────────────────── web cũng nhận ────────────────────
```

**① Web → backend.** REST cho thao tác ghi, GraphQL cho dữ liệu đọc của màn hình. Trình duyệt không
gọi backend trực tiếp: nó gọi `/api/*` của chính Next.js, server đó mới chuyển tiếp. Nhờ vậy API
token không bao giờ xuống trình duyệt, và đổi địa chỉ backend không cần build lại ảnh web.

**② AI → backend.** Qua `trellis-sdk`: HTTP có retry, tôn trọng `429`/`Retry-After`, tự làm mới
token, và đổi body lỗi ProblemDetail thành exception có kiểu.

**③ Backend → AI.** Một chiều, qua Kafka. Backend ghi outbox trong cùng transaction với dữ liệu,
relay gửi sau commit. **Backend không bao giờ gọi vào `trellis-ai`**: nếu cần kết quả của AI, nó
phát một event và chờ AI gọi ngược lại qua ②.

**④ Contract.** Không phải đường chạy lúc runtime, nhưng là đường quan trọng nhất về lâu dài: xem
mục 3.

**Không có đường nào khác.** Cụ thể: web không nói chuyện trực tiếp với ai; ai không đọc database
của backend; không repo nào import source của repo khác.

---

## 2. Vì sao chọn như vậy

**Vì sao AI không đọc thẳng database của backend?** Đọc thẳng thì nhanh hơn, nhưng nó biến mọi cột
thành API công khai: đổi tên một cột sẽ làm hỏng worker mà không ai biết cho tới lúc chạy. Qua event
và SDK thì cái gì chia sẻ là có chủ đích, và được mô tả trong `contracts/`.

Ngoại lệ duy nhất: worker **ghi** vào bảng inbox và bảng read model của chính nó. Hai bảng đó do
Liquibase của backend tạo, nhưng dữ liệu thuộc về worker.

**Vì sao backend không gọi AI?** Vì sẽ thành phụ thuộc vòng: AI đã gọi backend rồi. Một vòng đồng bộ
nghĩa là AI chậm thì request của người dùng chậm theo, và AI chết thì backend chết theo. Phát event
thì backend không cần biết có ai nghe hay không.

**Vì sao web phải làm BFF?** Để token nằm ở server. Nếu trình duyệt giữ token thì bất kỳ script nào
trên trang cũng đọc được.

---

## 3. Giữ ba bên không lệch nhau

Đây là rủi ro thật của mô hình nhiều repo: backend đổi một field, hai repo kia vẫn build xanh, và
lỗi chỉ nổ lúc chạy. Bốn lớp chặn:

| Lớp | Chặn điều gì |
| --- | --- |
| `contracts/` là nguồn duy nhất | không ai gõ tay lại shape của event hay API |
| `./gradlew build` kiểm tra contract đã export | sửa controller mà quên export là build đỏ ngay ở backend |
| Client sinh từ contract, CI kiểm tra lệch | web và ai không thể dùng bản cũ mà không ai biết |
| Vector dùng chung | `Backoff` và `StableId` hai bên phải ra cùng kết quả |

Chi tiết về hai package publish và quy trình phát hành: [contracts.md](contracts.md).

**Chưa có** cổng kiểm tra tương thích: hiện chưa có gì chặn việc xoá một field bắt buộc khỏi
`notice.published/v1`. Thêm field thì an toàn; đổi tên hoặc xoá thì phải tạo `v2` và phát cả hai cho
tới khi consumer chuyển xong. Hiện quy tắc này do con người giữ, không phải CI.

---

## 4. Một thay đổi đi qua ba repo thế nào

Ví dụ: thêm `publishedBy` vào event `notice.published`.

1. **backend** — sửa `contracts/events/notice.published/v1.schema.json` (thêm field là thay đổi tương
   thích), cho producer điền giá trị, chạy `./gradlew :trellis-app:exportApiContracts`, commit
   `contracts/`.
2. **backend** — tag `contracts-vX.Y.Z`, CI publish hai package.
3. **ai** — nâng pin `trellis-contracts`, model sinh lại có field mới, dùng nó trong worker.
4. **web** — nâng pin `@trellis/contracts` nếu field đó hiện lên giao diện.

Thứ tự này bắt buộc: consumer không bao giờ chạy trước producer. Nếu đảo lại, worker sẽ đòi một field
mà backend chưa gửi.

---

## 5. Chạy cả ba cùng lúc

Stack local thuộc về backend (T23). `trellis-web` và `trellis-ai` không có compose hạ tầng riêng,
chúng trỏ vào stack đang chạy:

```bash
cd trellis-backend && build-logic/scripts/dev/trellis-dev.sh up --profile full
```

Từng bước chi tiết: [../guide/running.md](../guide/running.md).

Hai lỗi hay gặp khi chạy cả ba:

- **401 dù token mới lấy** — lệch issuer giữa host và container. Compose đã xử lý; nếu tự chạy
  Keycloak thì `KC_HOSTNAME` và `TRELLIS_OIDC_JWK_SET_URI` phải khớp nhau.
- **Worker không nhận event** — kiểm tra `TRELLIS_EVENTS_TRANSPORT=kafka`. Mặc định là `inmemory`,
  tức worker chỉ nghe trong tiến trình của chính nó.

---

## 6. Thêm một repo nữa thì sao

Trước khi tạo repo thứ tư, cần trả lời: nó nói chuyện với ba repo kia qua đường nào trong mục 1? Nếu
câu trả lời là "đọc thẳng database" hoặc "backend gọi sang nó đồng bộ", thì nó chưa nên là một repo
riêng, mà nên là một module trong backend hoặc một worker trong `trellis-ai`.
