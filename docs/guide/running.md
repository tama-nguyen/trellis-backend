# Chạy Trellis

Hướng dẫn chạy cả ba repo trên máy. Tài liệu này nằm ở repo backend vì stack local thuộc về nó
(quyết định T23); hai repo kia chỉ cần trỏ vào stack đang chạy.

| Repo | Thư mục | Chạy cái gì |
| --- | --- | --- |
| `trellis-backend` | `~/Documents/projects/trellis-backend` | API Java, hạ tầng Docker, contract |
| `trellis-web` | `~/Documents/projects/trellis-web` | giao diện Next.js, kiêm BFF |
| `trellis-ai` | `~/Documents/projects/trellis-ai` | worker Kafka, RAG, SDK, CLI |

---

## 1. Cần cài gì

| Công cụ | Bản | Ghi chú |
| --- | --- | --- |
| JDK | **21** | JDK 26 làm hỏng Lombok và một số plugin. Dùng `mise` hoặc đặt `JAVA_HOME` tay. |
| Node | 22 | kèm `pnpm` |
| Python | 3.12 | kèm `uv` |
| Docker | có compose v2 ≥ 2.24 | `env_file: required` và `depends_on: required` cần bản này |

```bash
mise install            # đọc mise.toml của từng repo
```

Không dùng `mise` thì đặt tay mỗi lần mở terminal cho backend:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

---

## 2. Bật hạ tầng

Mọi thứ đi qua một script duy nhất, không gọi `docker compose` trực tiếp:

```bash
cd ~/Documents/projects/trellis-backend
build-logic/scripts/dev/trellis-dev.sh up --profile full
```

| Profile | Gồm |
| --- | --- |
| `minimal` | PostgreSQL |
| `app` | thêm backend, web, worker, job `setup` |
| `full` | thêm Kafka, Redis, MinIO, Keycloak, OTel Collector, Prometheus, Grafana, Tempo, Loki |
| `debug` | như `app`, mở cổng debug JDWP và debugpy |

Job `setup` chạy một lần rồi thoát 0: nó chạy Liquibase, tạo topic Kafka và tạo bucket MinIO. Các
service khác chờ nó xong mới khởi động.

```bash
build-logic/scripts/dev/trellis-dev.sh status   # trạng thái từng service
build-logic/scripts/dev/trellis-dev.sh ports    # bảng cổng kèm URL
build-logic/scripts/dev/trellis-dev.sh logs backend -f
build-logic/scripts/dev/trellis-dev.sh down     # dừng, giữ dữ liệu
build-logic/scripts/dev/trellis-dev.sh reset    # dừng và xoá sạch volume của slot này
```

### Cổng

| Service | Cổng | URL |
| --- | --- | --- |
| Backend API | 18080 | http://localhost:18080 |
| Backend actuator | 18081 | http://localhost:18081/actuator/health |
| Web | 13000 | http://localhost:13000 |
| PostgreSQL | 15432 | user/db `trellis` |
| Kafka | 19092 | |
| Redis | 16379 | |
| MinIO | 19000 / 19001 | console ở 19001 |
| Keycloak | 18180 | realm `trellis` |
| Prometheus | 19090 | http://localhost:19090 |
| Grafana | 13300 | http://localhost:13300 |
| Tempo / Loki | 13200 / 13100 | đã cắm sẵn vào Grafana |
| OTLP gRPC / HTTP | 14317 / 14318 | |

Mỗi git worktree là một **slot** riêng: slot N cộng thêm N×1000 vào mọi cổng và dùng tên project
compose riêng, nên chạy song song nhiều nhánh được. `trellis-dev.sh slot` cho biết slot hiện tại.

---

## 3. Chạy backend

Profile `app` và `full` đã chạy sẵn backend trong container. Khi sửa code thì chạy từ source:

```bash
cd ~/Documents/projects/trellis-backend
build-logic/scripts/dev/trellis-dev.sh up --profile full
./gradlew :trellis-app:bootRun          # API :8080, actuator :8081
```

Chạy từ source thì cổng là 8080 và 8081, không phải 18080 và 18081 — 1xxxx là cổng của container.

Hai chế độ chạy một lần rồi thoát:

```bash
./gradlew :trellis-app:bootRun --args='--spring.profiles.active=dev,setup'    # Liquibase + topic + bucket
./gradlew :trellis-app:bootRun --args='--spring.profiles.active=dev,migrate'  # chỉ migration và backfill
```

---

## 4. Chạy web

Không cần backend, dùng mock trong trình duyệt:

```bash
cd ~/Documents/projects/trellis-web
pnpm install
pnpm dev                      # http://localhost:3000 → chuyển hướng sang /vi
```

Chạy với backend thật:

```bash
TRELLIS_BACKEND_URL=http://localhost:18080 pnpm dev
```

Trình duyệt luôn gọi `/api/*` của chính Next.js; server này mới chuyển tiếp sang backend. Nhờ vậy
token không bao giờ xuống trình duyệt. Biến `NEXT_PUBLIC_MOCK_DOMAINS` nhận `true`, `false`, hoặc
danh sách domain cách nhau bằng dấu phẩy, ví dụ `auth,notice`, để mock từng phần.

---

## 5. Chạy worker và RAG

```bash
cd ~/Documents/projects/trellis-ai
uv sync
uv run trellis --help
uv run trellis rag --help
```

Chạy worker với Kafka và Postgres của stack:

```bash
export TRELLIS_EVENTS_TRANSPORT=kafka
export TRELLIS_KAFKA_BOOTSTRAP_SERVERS=localhost:19092
export TRELLIS_DATABASE_URL=postgresql://trellis:trellis@localhost:15432/trellis
uv run trellis worker run notice-stats
```

Worker mở sẵn endpoint kiểm tra sức khoẻ: `/health/live`, `/health/ready`, `/metrics`.

```bash
uv run trellis worker health notice-stats     # exit 0 nếu ready
```

Chạy không cần Kafka:

```bash
TRELLIS_EVENTS_TRANSPORT=inmemory uv run trellis worker run notice-stats \
  --input ../trellis-backend/contracts/examples/notice.published.v1.json
```

---

## 6. Gọi thử API

Lấy token từ Keycloak (realm `trellis`, client `trellis-cli`; tài khoản dev: `reader`, `staff`,
`admin`, mật khẩu là `<tên>-dev`):

```bash
TOKEN=$(curl -s -X POST http://localhost:18180/realms/trellis/protocol/openid-connect/token \
  -d grant_type=password -d client_id=trellis-cli -d username=admin -d password=admin-dev \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
```

Token này dùng được cho **cả hai cách chạy backend**: chạy từ source ở cổng 8080, và chạy trong
container ở cổng 18080. Lý do: Keycloak được cấu hình phát token với issuer là
`http://localhost:18180/realms/trellis` (`KC_HOSTNAME` trong compose), còn backend trong container
kiểm tra đúng chuỗi issuer đó nhưng lấy khoá qua mạng docker
(`TRELLIS_OIDC_JWK_SET_URI=http://keycloak:8080/...`). Nếu để backend tự suy khoá từ issuer thì nó
sẽ gọi `localhost` của chính container và hỏng.

```bash
# Tạo notice
curl -s -X POST http://localhost:8080/api/notices \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Hello","body":"First notice.","audience":"ALL","severity":"INFO"}'

# Publish: ETag của bản vừa tạo là "1"; Idempotency-Key cho phép gửi lại an toàn
curl -s -X POST http://localhost:8080/api/notices/NT-XXXX-XXXX-XXXX/publish \
  -H "Authorization: Bearer $TOKEN" -H 'If-Match: "1"' -H 'Idempotency-Key: demo-1'

# GraphQL
curl -s -X POST http://localhost:8080/api/graphql \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"query":"{ notice(code: \"NT-XXXX-XXXX-XXXX\") { code status stats { publishCount } } }"}'
```

Kiểm tra event đã chạy hết đường:

```bash
docker exec trellis-postgres psql -U trellis -d trellis \
  -c "select event_type, status from outbox_event order by id desc limit 5;"
docker exec trellis-postgres psql -U trellis -d trellis \
  -c "select * from notice_stats;"
```

Quan sát: Prometheus ở 19090, Grafana ở 13300 (đã cắm Tempo cho trace và Loki cho log).

---

## 7. Lệnh kiểm tra trước khi tạo merge request

```bash
cd ~/Documents/projects/trellis-backend
./gradlew build spotlessCheck
```

```bash
cd ~/Documents/projects/trellis-web
pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check && pnpm build && pnpm budget:check
```

```bash
cd ~/Documents/projects/trellis-ai
uv lock --check && uv run ruff check && uv run mypy && uv run pytest
uv run pytest -m integration        # cần Docker, tự bật container riêng
```

---

## 8. Gặp sự cố

| Triệu chứng | Nguyên nhân và cách xử lý |
| --- | --- |
| 401 `UNAUTHENTICATED` dù token mới lấy | Lệch issuer. Compose đã xử lý sẵn (`KC_HOSTNAME` + `TRELLIS_OIDC_JWK_SET_URI`); nếu bạn đổi cổng Keycloak hoặc tự chạy Keycloak thì phải chỉnh cả hai cho khớp |
| `./gradlew` lỗi lạ về Lombok hoặc plugin | Đang chạy JDK sai. `java -version` phải là 21. |
| Cổng đã bị chiếm | Dùng slot khác: `trellis-dev.sh --slot 1 up --profile full`, mọi cổng cộng 1000 |
| `checkApiContracts` fail | Đã sửa controller hoặc `.graphqls` mà quên export. Chạy `./gradlew :trellis-app:exportApiContracts` rồi commit `contracts/` |
| IDE không nhận ra module Gradle | Bấm **Reload Gradle Project**. `.idea/` nằm trong `.gitignore` nên có thể còn trỏ đường dẫn cũ sau khi đổi thư mục |
| Liquibase báo checksum sai | Changeset đã chạy bị sửa, hoặc file bị đổi đường dẫn. Với DB dev: `trellis-dev.sh reset` |
| Worker không nhận event | Kiểm tra `TRELLIS_EVENTS_TRANSPORT=kafka` và bảng `inbox_event`: event trùng sẽ bị bỏ qua đúng theo thiết kế |
| MinIO không kéo được image | Image chính hãng đã ngừng phát hành; compose dùng bản cộng đồng ghim theo digest. Đổi bằng `TRELLIS_MINIO_IMAGE` |

---

## 9. Mức độ đã kiểm chứng

Viết ngày 2026-11-14, sau khi tách ba repo.

**Đã chạy thật hôm nay:** `trellis-dev.sh up --profile full` (13 service lên healthy, job `setup`
thoát 0, image backend tự build), `status`, `ports`, `/actuator/health/readiness` trả 200, lấy token
từ Keycloak rồi gọi backend **trong container**: tạo notice trả 201, publish trả 200, GraphQL trả
`publishCount: 1`, bảng `outbox_event` có dòng `notice.published -> PUBLISHED` và `notice_stats`
đếm bằng 1.

**Đã chạy ở các bước trước, không chạy lại hôm nay:** worker Python đếm đúng một lần dù event phát
lại, và các lệnh kiểm tra của web và `trellis-ai`.

**Chưa chạy bao giờ:** chế độ `migrate` ở mục 3.
