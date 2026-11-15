# Thêm một module nghiệp vụ

Hướng dẫn đi từ lệnh sinh module tới một API chạy được, lấy `course` làm ví dụ. Mọi lệnh và mọi
kết quả trong tài liệu này đều được chạy thật.

---

## 1. Vì sao có lệnh sinh

Thêm một nghiệp vụ không chỉ là tạo vài file Java. Nếu làm tay, bạn phải tạo đủ 6 thứ dưới đây,
thiếu một cái là không chạy, và thiếu cái cuối thì chạy nhưng mọi request trả 403:

| Phải có | Thiếu thì sao |
| --- | --- |
| `build.gradle.kts` của module | Gradle không build module |
| 17 package con, mỗi cái một `package-info.java` | ArchUnit báo sai tầng |
| `<Name>Module` + `AutoConfiguration.imports` | Spring không thấy bean nào |
| Changeset Liquibase + dòng include vào master | bảng không được tạo |
| Dòng trong `settings.gradle.kts` và dependency ở `trellis-app` | module không nằm trong app |
| Permission được map cho role trong profile | mọi lời gọi trả 403 |

Không thể commit sẵn các thư mục rỗng thay cho việc này: git không lưu thư mục rỗng, và 5 việc còn
lại vẫn phải làm tay.

---

## 2. Chạy một lệnh

```bash
build-logic/scripts/dev/new-module.sh --name course --aggregate Course --plural courses
```

Thêm `--dry-run` để xem trước mà không ghi gì. Kết quả: **49 file**, trong đó đáng chú ý:

```text
trellis-course/
├── build.gradle.kts
├── README.md                          năm chỗ cần sửa cho nghiệp vụ thật
└── src/main/
    ├── java/io/trellis/course/
    │   ├── CourseModule.java           auto-configuration: quét application/, infrastructure/, web/
    │   ├── api/                        thứ module khác được phép import
    │   │   ├── dto/CourseStatus.java
    │   │   ├── error/CourseErrorCode.java, CourseErrors.java
    │   │   ├── event/                  (rỗng: chưa phát event nào)
    │   │   └── security/CoursePermissions.java      course:read, course:manage
    │   ├── domain/                     không Spring, không SQL
    │   │   ├── model/Course.java, CourseDetails.java
    │   │   ├── value/CourseCode.java   mã CO-XXXX-XXXX-XXXX
    │   │   └── policy/CourseVisibility.java, CourseReader.java
    │   ├── application/
    │   │   ├── command/CourseCommands.java      ghi: @Transactional, @RequiresPermission, @Audited
    │   │   ├── query/CourseQueries.java         đọc: read-only
    │   │   ├── port/CourseRepository.java       interface, không biết SQL
    │   │   ├── view/CourseView.java, CourseSummary.java
    │   │   └── validation/CourseValidator.java
    │   ├── infrastructure/
    │   │   ├── persistence/JdbcCourseRepository.java    SQL thật, keyset paging
    │   │   ├── messaging/              (rỗng: chưa có consumer)
    │   │   └── client/                 (rỗng: chưa gọi dịch vụ ngoài)
    │   └── web/
    │       ├── rest/CourseController.java, CourseDetailsRequest.java, CoursePage.java
    │       └── graphql/CourseGraphQlController.java
    └── resources/
        ├── META-INF/spring/...AutoConfiguration.imports
        ├── db/changelog/course/001-course.sql
        └── graphql/course/course.graphqls
```

Lệnh cũng tự sửa `settings.gradle.kts`, `trellis-app/build.gradle.kts` và master changelog.

Ba package `api/event`, `infrastructure/messaging`, `infrastructure/client` chỉ có `package-info.java`.
Giữ chúng là cố ý: khi cần phát event hay gọi dịch vụ ngoài, bạn biết ngay phải đặt vào đâu.

Build ngay được:

```bash
./gradlew build          # BUILD SUCCESSFUL
```

---

## 3. Cấp quyền, nếu không mọi thứ trả 403

Module sinh ra khai hai permission `course:read` và `course:manage`, nhưng **chưa ai có chúng**.
Việc map role sang permission là chuyện của từng môi trường, nên nó nằm trong file profile, không
nằm trong `application.yml`. Với máy local, sửa `application-dev.yml`:

```yaml
trellis:
  security:
    role-permissions:
      platform-admin:
        - course:read
        - course:manage
```

Môi trường thật thì map role của IdP bên đó, trong profile của chính nó.

---

## 4. Thử API

Bật hạ tầng và chạy app (xem [running.md](running.md) nếu đây là lần đầu):

```bash
build-logic/scripts/dev/trellis-dev.sh up --profile full
./gradlew :trellis-app:bootRun
```

```bash
TOKEN=$(curl -s -X POST http://localhost:18180/realms/trellis/protocol/openid-connect/token \
  -d grant_type=password -d client_id=trellis-cli -d username=admin -d password=admin-dev \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
```

**Tạo** — trả 201, kèm `Location` và `ETag`:

```bash
curl -i -X POST http://localhost:8080/api/courses -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Tiếng Anh B1","description":"Khoá tổng quát trình độ trung cấp."}'
```

```text
Location: /api/courses/CO-QWTC-0ER2-GKX1
ETag: "1"
{"code":"CO-QWTC-0ER2-GKX1","name":"Tiếng Anh B1","status":"DRAFT","version":1, ...}
```

**Đổi trạng thái** và **đọc lại**:

```bash
curl -X POST "http://localhost:8080/api/courses/$CODE/activate" -H "Authorization: Bearer $TOKEN" -H 'If-Match: "1"'
curl -s "http://localhost:8080/api/courses/$CODE" -H "Authorization: Bearer $TOKEN"
# {"code":"CO-QWTC-0ER2-GKX1", ... "status":"ACTIVE","version":2, ...}
```

**Sửa với ETag cũ** — bị chặn, đây là optimistic locking đang làm việc:

```text
412  {"detail":"Course CO-QWTC-0ER2-GKX1 is at version 2, not 1",
      "type":"urn:trellis:problem:conflict", ...}
```

**Thiếu quyền** (tài khoản `reader` không có `course:manage`): `403`.
**Không token**: `401` với `type: urn:trellis:problem:unauthenticated`.

Danh sách dùng cursor, không dùng offset:

```bash
curl -s "http://localhost:8080/api/courses?first=2" -H "Authorization: Bearer $TOKEN"
# {"items":[...],"nextCursor":"...","hasNext":true}
```

---

## 5. Từ khung sang nghiệp vụ thật

Khung sinh ra cố tình nghèo nàn: một `Course` chỉ có tên, mô tả và vòng đời DRAFT → ACTIVE →
ARCHIVED. Năm chỗ cần sửa, theo thứ tự này:

1. **`domain/model/Course.java`** — thêm dữ liệu và **luật** thật: `language`, `cefrLevel`,
   `lessonCount`, và các quy tắc kiểu "khoá đã ACTIVE thì không đổi ngôn ngữ". Luật nghiệp vụ
   thuộc về domain, không phải controller, vì mọi đường vào (REST, GraphQL, consumer) đều phải
   chịu cùng một luật.
2. **`domain/value/`** — thứ có quy tắc riêng thì tách thành value object: `CefrLevel` chỉ nhận
   A1…C2, `LanguageCode` chuẩn hoá chữ thường. Đừng để chúng là `String`.
3. **`application/validation/CourseValidator.java`** — kiểm tra đầu vào, gom hết lỗi rồi trả một
   lần, thay vì chết ở lỗi đầu tiên.
4. **`resources/db/changelog/course/001-course.sql`** — cột tương ứng. Chưa chạy production thì sửa
   thẳng changeset này; đã chạy rồi thì thêm `002-...sql`, không bao giờ sửa changeset cũ.
5. **`web/rest/CourseController.java`** — endpoint còn thiếu. Controller chỉ đọc request, gọi
   `CourseCommands`/`CourseQueries` và trả về; không chứa `if` nghiệp vụ nào.

---

## 6. Khi nào thêm gì

| Cần | Làm ở đâu | Xem mẫu |
| --- | --- | --- |
| Báo cho hệ thống khác biết (gửi mail, cập nhật thống kê) | `api/event/` khai payload, `CourseCommands` gọi outbox trong cùng transaction | `trellis-notice` với `notice.published` |
| Màn hình cần dữ liệu tổng hợp nhiều nguồn | `web/graphql/` + `course.graphqls`, dùng `@BatchMapping` để tránh N+1 | `NoticeGraphQlController` |
| Số liệu đọc nhiều, tính nặng | read model riêng: `infrastructure/messaging/` cập nhật theo event, kèm `ReadModelRebuilder` để dựng lại | `NoticeStatsProjection` |
| Phản ứng với event của module khác | `infrastructure/messaging/` với một `EventHandler` | `NoticeStatsProjection` |
| Gọi API bên ngoài | `infrastructure/client/` | chưa có mẫu |

Nguyên tắc: `api/` là thứ **module khác được import**; mọi thứ còn lại là nội bộ.

---

## 7. Lỗi hay gặp

| Triệu chứng | Nguyên nhân |
| --- | --- |
| Mọi request trả 403 | chưa map permission cho role, mục 3 |
| Bảng không tồn tại | quên dòng include trong master changelog (lệnh sinh làm sẵn, nhưng changeset thứ hai thì bạn tự thêm) |
| ArchUnit đỏ | đặt class sai tầng, ví dụ gọi `JdbcClient` từ `application/` |
| Logic nghiệp vụ không chạy khi gọi từ GraphQL | luật nằm trong controller REST thay vì trong domain hoặc application |
| `@Transactional` không có tác dụng | đặt trên method private, hoặc gọi nội bộ qua `this` nên proxy không chạy |
| `@Idempotent` không chặn request lặp | key SpEL trỏ sai field, nên mỗi lần gọi ra một key khác |

---

## 8. Xoá một module

```bash
rm -rf trellis-course
```

rồi gỡ ba chỗ nối: dòng trong `settings.gradle.kts`, dependency trong `trellis-app/build.gradle.kts`,
và dòng include trong master changelog. Nếu đã chạy trên database nào thì cần thêm một changeset
xoá bảng. Chạy `./gradlew build` để xác nhận.
