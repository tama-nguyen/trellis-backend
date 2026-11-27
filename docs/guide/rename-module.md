# Đổi tên module: bản đồ tham chiếu và công cụ sinh code

Đọc file này **thay cho việc khảo sát lại cả 3 repo**. Nó ghi tên module xuất hiện ở đâu, file nào
sinh bằng công cụ nào, và những chỗ dễ sai. Rút ra từ lần đổi `notice` -> `notification`
(tháng 11/2026), khi đó khoảng 240 file ở 3 repo thay đổi.

Ký hiệu: `<old>` / `<new>` là tên viết thường, ví dụ `notification`.

## 1. Chốt phạm vi trước

| Phạm vi | Gồm | Hệ quả |
| --- | --- | --- |
| Chỉ module | thư mục, Gradle project, package, `<Name>Module` | không đổi contract |
| Module + code | thêm class Java | không đổi contract |
| Toàn bộ | thêm bảng, quyền, role, REST, GraphQL, event, worker | breaking change; sửa cả web và ai |

Sửa changeset cũ **chỉ được** khi chủ repo xác nhận chưa môi trường nào có dữ liệu, và sau đó phải tạo
DB mới (`build-logic/scripts/dev/trellis-dev.sh reset --yes`). Ngược lại thì thêm changeset mới.

## 2. Khảo sát nhanh (chạy trong từng repo)

```bash
git grep -il <old> -- . ':!*lock*'
```

```bash
git ls-files | grep -i <old>
```

Thay thế đủ 3 dạng chữ (`old`, `Old`, `OLD`) trong nội dung, sau đó `git mv` các đường dẫn.

**Không được thay:** `LICENSE`, `NOTICE`, `NOTICE.txt`, các mục cũ trong
`docs/how/breaking-changes.md`, và từ trùng tên nhưng mang nghĩa tiếng Anh thường (đọc từng dòng grep).

## 3. trellis-backend

| Nhóm | Vị trí |
| --- | --- |
| Gradle | `settings.gradle.kts`, `trellis-app/build.gradle.kts`, comment trong `build.gradle.kts` |
| Module | `trellis-<old>/`: package, `<Name>Module`, `AutoConfiguration.imports`, `db/changelog/<old>/`, `graphql/<old>/`, `README.md` |
| Cấu hình app | `application.yml` (topic, feature flag), `config/rate-limit.yml`, `FeatureFlags.java` |
| Database | `db.changelog-schema.yaml`, `trellis-platform/.../platform/security/002-platform-roles.sql`, `trellis-app/.../db/operations/identity/*.sql` |
| Bảo mật | `SecurityAction.java`, `build-logic/docker/keycloak/trellis-realm.json` |
| Test | `trellis-app/src/test/java/io/trellis/app/<old>/`, `ArchitectureTest`, `ContentValidationTest`, `OidcPrincipalMigrationTest`, `PrincipalMigrationFixtures`, `EnvelopeContractTest`, `GetCurrentUserUseCaseTest` |
| CI, tooling | `build-logic/ci/common.yml`, `build-logic/scripts/ci/check_ci_refs.py`, `build-logic/scripts/dev/new_module.py` |
| Contract viết tay | `contracts/events/`, `contracts/examples/`, `contracts/testdata/`, `contracts/codegen/python/generate_sdk.py` |
| Tài liệu | `AGENTS.md`, `README.md`, `docs/`, `.agent-skills/` |

Module mẫu còn được nhắc làm ví dụ trong Javadoc của `trellis-common` và `trellis-platform*`.

### File sinh tự động (không sửa tay, chạy theo thứ tự)

| Lệnh | Sinh ra |
| --- | --- |
| `./gradlew spotlessApply` | format và sắp lại import |
| `./gradlew :trellis-app:exportApiContracts` (cần Docker) | `contracts/openapi/openapi.json`, `contracts/graphql/schema.graphqls` |
| `build-logic/scripts/contracts/generate.sh` (cần `uv`, `pnpm`) | `contracts/packages/python/src/`, `contracts/packages/npm/{src,schema.graphqls}` |
| `pnpm build` trong `contracts/packages/npm` | `dist/` (có commit vào git) |
| `./gradlew build` | compile, test, kiểm tra contract lệch |
| `python3 build-logic/scripts/ci/check_ci_refs.py` | kiểm tra đường dẫn CI |

JDK 21: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.

### Bẫy

- `contracts/testdata/stable-id.json`: đổi `namespace` hoặc tên field thì `uuid` đổi theo. Tính lại theo
  công thức trong trường `description` của file: 16 byte đầu của
  `SHA-256(namespace || 0x00 || canonicalJson)`, đặt version 8 và variant RFC 4122.
- Thư mục `trellis-<old>/build/` còn sót sau `git mv` (file bị ignore): xoá tay, rồi import lại Gradle
  trong IDE.
- Mã tham chiếu (`NT-...`) là hằng `PREFIX` trong value object, không tự đổi theo tên.
- Tên dài hơn làm lệch cột trong SQL và bảng Markdown; kiểm tra thêm độ dài các cột `VARCHAR`.
- Lệnh `cd web && pnpm codegen` và `cd workers && ...` nhắc ở vài tài liệu là của bố cục cũ; hai thư
  mục đó không còn trong repo này.

## 4. trellis-web

**Không có generator.** `src/shared/api/generated/` và `src/shared/graphql/generated/` được commit sẵn
và repo backend cũng chưa có cấu hình sinh ra chúng, nên khi đổi tên phải thay thế cơ học cả hai thư
mục (cả tên file) và nói rõ trong mô tả thay đổi. Cách này hết cần khi `@trellis/contracts` được publish
(xem `scripts/check-contracts.mjs`).

Chỗ cần xem: `src/{entities,features}/<old>/`, `src/app/[locale]/(site)/<old>s/`, `src/mocks/`,
`src/i18n/messages/{en,vi,zh}/`, `src/shared/`, `e2e/`, `scripts/budget.json` (khoá là đường dẫn route),
`.env.example`.

Kiểm tra bằng `npm run <script>`: `typecheck`, `lint`, `format:check`, `test`, `i18n:check`,
`contracts:check`, `build`, `budget:check`.

- Đổi route xong mà `typecheck` báo lỗi trong `.next/`: đó là type cũ. Chạy
  `rm -rf .next/types .next/dev/types && npx next typegen`.
- Tên dài hơn làm `format:check` fail: chạy `npx prettier --write` trên các file được báo.

## 5. trellis-ai

**Không có generator.** Sửa cơ học `packages/trellis-events/src/trellis_events/generated/` và
`packages/trellis-sdk/src/trellis_sdk/generated/`.

Chỗ cần xem: `apps/<old>-stats/`, `contracts-snapshot/`, `pyproject.toml`, `Dockerfile`,
`.gitlab-ci.yml`, `tests/`.

1. **Commit backend trước**, rồi chạy `scripts/update-contracts-snapshot.sh` (script ghi hash commit
   backend vào `SOURCE.txt`).
2. `uv lock`, `uv sync`.
3. `uv run ruff check --fix`, `uv run ruff format`.
4. `uv run mypy`, `uv run pytest`, `uv run pytest -m integration` (cần Docker).

- `Failed to spawn: mypy` hoặc `pytest`: `.venv` được tạo ở đường dẫn khác. Dùng
  `uv run python -m mypy`, `uv run python -m pytest`.
