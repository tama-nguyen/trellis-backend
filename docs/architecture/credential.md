Stack đang tắt. Bật lại rồi kiểm chứng từng credential:

Stack lúc bạn gửi ảnh đang tắt, tôi bật lại rồi đăng nhập thử từng cái — tất cả đều đúng. Đã ghi thành mục **2b** trong [docs/guide/running.md](docs/guide/running.md):

| Dịch vụ | Cổng host | User | Mật khẩu | Biến để đổi |
|---|---|---|---|---|
| `postgres-1` | 15432 | `trellis` | `trellis` | `TRELLIS_DB_PASSWORD` |
| `keycloak-1` (admin console) | 18180 | `admin` | `admin` | `TRELLIS_KEYCLOAK_ADMIN_PASSWORD` |
| `mailpit-1` (SMTP + UI) | 11025 / 18025 | — | — | `TRELLIS_MAILPIT_SMTP_PORT`, `TRELLIS_MAILPIT_HTTP_PORT` |
| `minio-1` (API + console) | 19000 / 19001 | `trellis` | `trellis-dev-secret` | `TRELLIS_MINIO_ROOT_PASSWORD` |
| `grafana-1` | 13300 | `admin` | `admin` | `TRELLIS_GRAFANA_ADMIN_PASSWORD` |
| `redis-1` | 16379 | — | không mật khẩu | — |
| `kafka-1` | 19092 | — | PLAINTEXT, không auth | — |
| `prometheus-1` / `tempo-1` / `loki-1` / `otel-collector-1` | 19090 / 13200 / 13100 / 14317 | — | không auth | — |
| `backend-1` (profile `app`) | 18080 | — | native opaque token hoặc JWT Keycloak | — |

Ba tài khoản trong realm `trellis`, client public `trellis-cli` và `trellis-web` (**không có client secret**, vì là public client):

| User | Mật khẩu | Realm role | Permission |
|---|---|---|---|
| `reader` | `reader-dev` | `notice-reader` | `notice:read` |
| `staff` | `staff-dev` | `staff` | `notice:read`, `notice:read-staff` |
| `admin` | `admin-dev` | `notice-admin`, `platform-admin` | notice đầy đủ + `course:read`, `course:manage` |

Kết quả kiểm chứng vừa chạy: psql → `trellis@trellis`; redis → `PONG`; Grafana `admin:admin` → 200, và **anonymous cũng 200** (compose bật `GF_AUTH_ANONYMOUS_ENABLED` role Viewer, nên xem dashboard không cần đăng nhập, chỉ sửa mới cần); Keycloak master `admin/admin` → token; cả ba user realm → 200; MinIO `mc ls` → thấy bucket `trellis/`.

Hai điểm tôi ghi rõ trong tài liệu vì dễ hiểu nhầm:

- Bảng **role → permission không nằm trong Keycloak**, và cũng không nằm trong file cấu hình: nó là bảng
  PostgreSQL `role_permission`, được seed bằng changeset Liquibase của từng module
  (`security/002-platform-roles.sql`, `notice/002-notice-roles.sql`, `learning-content/002-course-roles.sql`).
  Keycloak chỉ cấp role, backend quyết role đó làm được gì.
- Các giá trị này cố tình yếu và cố tình được commit, vì stack chỉ bind `127.0.0.1` (`TRELLIS_BIND`). Mỗi môi trường thật đặt giá trị riêng qua biến môi trường — cột cuối của bảng là tên biến tương ứng.


## Tài khoản native (`trellis-identity`)

Ngoài Keycloak, backend còn có đăng nhập email/mật khẩu của riêng nó (ADR 0003), lưu ở bảng `users`. Ba tài khoản dưới đây
được seed bởi changeset `identity-005-dev-users`, chỉ nằm trong `db.changelog-dev.yaml` — master của `test`
và `prod` không include nó.

| Email | Mật khẩu | Role được grant | Permission |
|---|---|---|---|
| `reader@example.test` | `reader-dev` | `notice-reader` | `notice:read` |
| `staff@example.test` | `staff-dev` | `staff` | `notice:read`, `notice:read-staff` |
| `admin@example.test` | `admin-dev` | `notice-admin`, `platform-admin` | notice đầy đủ + `course:*`, `platform.audit:read`, `platform.role-grants:manage` |

```bash
curl -s localhost:8080/api/auth/login -H 'content-type: application/json' \
  -d '{"email":"admin@example.test","password":"admin-dev"}'
```

Trả về `{accessToken, refreshToken}`; dùng `accessToken` làm `Authorization: Bearer ...` như token Keycloak.

Hai điểm dễ hiểu nhầm:

- **Token native không mang quyền.** `NativeTokenAuthenticationProvider` chỉ gắn đúng một role là
  `trellis.identity.default-role` (`learner`). Mọi quyền khác của ba tài khoản trên đến từ bảng `role_grant`,
  do changeset `identity-006-dev-user-grants` seed. Đây cũng là ví dụ mẫu của mô hình grant: muốn thêm
  quyền cho một tài khoản thì cấp grant, không sửa token.
- Mật khẩu là bcrypt cost 12, cố tình yếu và cố tình commit — giống phần còn lại của local stack, vốn chỉ
  bind `127.0.0.1`.
