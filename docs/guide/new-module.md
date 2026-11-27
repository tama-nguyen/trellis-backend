# Thêm nghiệp vụ mới: feature hay module?

Hướng dẫn đi từ quyết định "có cần module mới không" tới một API chạy được, lấy
`trellis-learning-content` làm ví dụ. Mọi lệnh và mọi kết quả trong tài liệu này
đều được chạy thật trên chính module này.

**Đổi tên một module đã chạy ở đâu đó thì cẩn thận**: đường dẫn changeset đổi theo, Liquibase coi đó là
changeset mới và `CREATE TABLE` lần hai sẽ lỗi `relation already exists`. Trên máy local thì
`build-logic/scripts/dev/trellis-dev.sh reset --yes` rồi chạy lại; môi trường đã có dữ liệu thì giữ
nguyên đường dẫn cũ, hoặc khai `logicalFilePath`.

---

## 1. Trước hết: phần lớn trường hợp **không** cần module mới

Đây là quyết định T26. Một module là **một nhóm feature có cùng vòng đời**, không phải một aggregate.
Ba tầng khái niệm khác nhau, đừng lẫn:

| Tầng | Ví dụ | Có Gradle module riêng? |
| --- | --- | --- |
| Business domain | Learning Management | không |
| Bounded context | Learning Content, Assessment, Billing | có, khi ranh giới là thật |
| Feature / use case | Create Course, Publish Lesson, Submit Quiz | không — chỉ là package và class |

30 use case **không** có nghĩa là 30 module. Nếu mỗi tính năng lại sinh một `trellis-*` thì sau vài năm
dự án thành **distributed monolith về mặt code**: vẫn một JVM, nhưng mỗi thay đổi phải đi qua năm
`build.gradle.kts`.

**Mặc định: thêm feature vào module đang sở hữu vòng đời đó** (mục 7). Chỉ tách module mới khi đạt điều
kiện T18 **và** trả lời được năm câu, viết thành một ADR ngắn trong `docs/adr/`:

1. Module này sở hữu nghiệp vụ và dữ liệu gì mà module hiện tại **không nên** sở hữu?
2. Public API của nó là gì, và module nào cần dùng?
3. Nếu không tách mà chỉ dùng Java package thì **vấn đề gì thực sự** xảy ra?
4. Tách có tạo dependency cycle, hoặc khiến một use case phải sửa nhiều module hơn, không?
5. Chi phí build, test và maintenance tăng thêm có xứng đáng?

Ví dụ cụ thể: `Course` và `Lesson` cùng vòng đời tạo — soạn — xuất bản nội dung, nên **cùng một module**;
tách sớm chỉ sinh thêm một chiều phụ thuộc giữa hai module. Ngược lại Billing đáng tách, vì invariant,
dữ liệu nhạy cảm, yêu cầu kiểm toán và integration với payment provider của nó khác hẳn.

| Tình huống | Làm gì |
| --- | --- |
| Thêm Create Lesson, Publish Course | feature package trong `trellis-learning-content` |
| Thêm quiz trắc nghiệm, rồi quiz Speaking | `trellis-assessment` (Speaking nhờ `trellis-ai` đánh giá) |
| Thêm FSRS để lên lịch ôn tập | `trellis-learning` hoặc `trellis-ai`, tuỳ ai sở hữu dữ liệu |
| Thêm thanh toán, hoá đơn, hoàn tiền | cân nhắc `trellis-billing` riêng |
| Thêm Elasticsearch | chưa cần module; bắt đầu bằng một search adapter trong `infrastructure/` |
| Thêm tính năng AI | mở rộng `trellis-ai`, **không** sinh module Gradle |

---

## 2. Vì sao có lệnh sinh module

Khi ADR nói "đúng, cần module mới", thì tạo nó không chỉ là tạo vài file Java. Phải đủ 6 thứ dưới đây,
thiếu một cái là không chạy, và thiếu cái cuối thì chạy nhưng mọi request trả 403:

| Phải có | Thiếu thì sao |
| --- | --- |
| `build.gradle.kts` của module | Gradle không build module |
| Package con của từng tầng | ArchUnit báo sai tầng |
| `<Module>Module` + `AutoConfiguration.imports` | Spring không thấy bean nào |
| Changeset Liquibase + dòng include vào master | bảng không được tạo |
| Dòng trong `settings.gradle.kts` và dependency ở `trellis-app` | module không nằm trong app |
| Permission được map cho role trong profile | mọi lời gọi trả 403 |

Không thể commit sẵn các thư mục rỗng thay cho việc này: git không lưu thư mục rỗng, và 5 việc còn lại
vẫn phải làm tay.

---

## 3. Chạy một lệnh

```bash
build-logic/scripts/dev/new-module.sh --slug learning-content --name course \
  --aggregate Course --plural courses --features course,lesson,authoring,publishing
```

Ba tham số đáng chú ý:

- `--slug` là **module**: thư mục `trellis-learning-content/`, project Gradle
  `:trellis-learning-content`, package `io.trellis.learningcontent` (bỏ gạch ngang).
- `--name` là **aggregate**: tên bảng, tiền tố permission (`course:read`), audit action
  (`course.created`), tên metric, tên biến.
- `--features` là các feature dùng chung module. Feature **đầu tiên phải trùng `--name`**: nó là feature
  được sinh code mẫu; các feature còn lại là package rỗng để bạn điền.

Thêm `--dry-run` để xem trước mà không ghi gì. Kết quả: **57 file**, trong đó đáng chú ý:

```text
trellis-learning-content/
├── build.gradle.kts
├── README.md                              năm chỗ cần sửa cho nghiệp vụ thật
└── src/main/
    ├── java/io/trellis/learningcontent/
    │   ├── LearningContentModule.java     auto-configuration: quét application/, infrastructure/, web/
    │   ├── api/                           phẳng: một mặt công bố cho cả module
    │   │   ├── dto/CourseStatus.java
    │   │   ├── error/CourseErrorCode.java, CourseErrors.java
    │   │   ├── event/                     (chưa có: module chưa phát event nào)
    │   │   └── security/CoursePermissions.java      course:read, course:manage
    │   ├── domain/                        chia theo feature, không Spring, không SQL
    │   │   ├── course/
    │   │   │   ├── model/Course.java, CourseDetails.java
    │   │   │   ├── value/CourseCode.java  mã CO-XXXX-XXXX-XXXX
    │   │   │   └── policy/CourseVisibility.java, CourseReader.java
    │   │   ├── lesson/                    (chưa có class)
    │   │   ├── authoring/                 (chưa có class)
    │   │   └── publishing/                (chưa có class)
    │   ├── application/                   chia theo feature
    │   │   ├── course/
    │   │   │   ├── usecase/authoring/            CreateCourseUseCase, UpdateCourseUseCase
    │   │   │   ├── usecase/publishing/           ActivateCourseUseCase, ArchiveCourseUseCase
    │   │   │   ├── usecase/reading/              GetCourseUseCase, ListCoursesUseCase
    │   │   │   ├── support/                      CourseAccess, CourseLoader (dùng chung)
    │   │   │   ├── port/CourseRepository.java     interface, không biết SQL
    │   │   │   ├── view/CourseView.java, CourseSummary.java
    │   │   │   └── validation/CourseValidator.java
    │   │   ├── lesson/, authoring/, publishing/    (chưa có class)
    │   ├── infrastructure/                phẳng: một bộ adapter cho cả module
    │   │   ├── persistence/JdbcCourseRepository.java   SQL thật, keyset paging
    │   │   ├── messaging/                 (chưa có class)
    │   │   └── client/                    (chưa có class)
    │   └── web/                           phẳng: một bộ cổng vào
    │       └── rest/CourseController.java, CourseDetailsRequest.java, CoursePage.java
    └── resources/
        ├── META-INF/spring/...AutoConfiguration.imports
        └── db/changelog/learning-content/001-course.sql
```

Lệnh cũng tự sửa `settings.gradle.kts`, `trellis-app/build.gradle.kts` và master changelog.

**Vì sao chỉ `domain/` và `application/` chia theo feature?** Vì chỉ hai tầng đó có nhiều cái để chia:
mỗi feature có model, luật và use case riêng. Còn `api/` là **một** mặt công bố của module,
`infrastructure/` là **một** bộ adapter, `web/` là **một** bộ cổng vào — chia tiếp chỉ làm rối mà không
tách được gì. Module một feature (như `trellis-notification`) thì bỏ hẳn tầng feature.

**Vì sao package không có gạch ngang và không lồng nhau?** `io.trellis.learningcontent`, không phải
`io.trellis.learning.content`. ArchUnit phân biệt module nghiệp vụ bằng **đúng một segment** sau
`io.trellis`, nên tên lồng sẽ bị tính là phần của module `learning` và mọi rule ranh giới mất tác dụng.

Build ngay được:

```bash
./gradlew build          # BUILD SUCCESSFUL
```

---

## 4. Cấp quyền, nếu không mọi thứ trả 403

Module sinh ra khai hai permission `course:read` và `course:manage`, nhưng **chưa ai có chúng**. Map role
sang permission là dữ liệu, không phải cấu hình: nó nằm trong bảng `role_permission`, và module tự seed phần
của mình bằng một changeset, thêm vào cuối `db.changelog-schema.yaml` (master dùng chung của mọi môi trường):

```sql
--liquibase formatted sql

--changeset trellis:course-002-role-permissions
--comment: Roles of the course module.
INSERT INTO role_permission (role_id, permission) VALUES
    ('0193a5c0-0001-7000-8000-000000000001', 'course:read'),
    ('0193a5c0-0001-7000-8000-000000000001', 'course:manage')
ON CONFLICT (role_id, permission) DO NOTHING;
--rollback DELETE FROM role_permission WHERE permission LIKE 'course:%';
```

Changeset chạy ở mọi môi trường. Sau đó mỗi môi trường tự sửa bảng bằng SQL nếu role của IdP bên đó khác;
không có gì ghi đè lại khi khởi động.

---

## 5. Thử API

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
HTTP/1.1 201
Location: /api/courses/CO-NH70-N3G2-R8XG
ETag: "1"
{"code":"CO-NH70-N3G2-R8XG","name":"Tiếng Anh B1","description":"Khoá tổng quát trình độ trung cấp.",
 "status":"DRAFT","version":1,"createdAt":"2026-11-15T15:17:03.929225Z","activatedAt":null,"archivedAt":null}
```

**Đổi trạng thái** và **đọc lại**:

```bash
curl -X POST "http://localhost:8080/api/courses/$CODE/activate" -H "Authorization: Bearer $TOKEN" -H 'If-Match: "1"'
curl -s "http://localhost:8080/api/courses/$CODE" -H "Authorization: Bearer $TOKEN"
# {"code":"CO-9XH9-68SP-SSD6","name":"Tiếng Trung HSK3","status":"ACTIVE","version":2, ...}
```

**Sửa với ETag cũ** — bị chặn, đây là optimistic locking đang làm việc:

```text
412  {"detail":"Course CO-9XH9-68SP-SSD6 is at version 2, not 1",
      "title":"Course was changed by someone else",
      "type":"urn:trellis:problem:conflict", "instance":"/api/courses/CO-9XH9-68SP-SSD6"}
```

**Thiếu quyền** (tài khoản `reader` không có `course:manage`): `403`.
**Không token**: `401` với `type: urn:trellis:problem:unauthenticated`.

Danh sách dùng cursor, không dùng offset:

```bash
curl -s "http://localhost:8080/api/courses?first=2" -H "Authorization: Bearer $TOKEN"
# {"items":[...2 phần tử...],"nextCursor":"...","hasNext":false}
```

---

## 6. Từ khung sang nghiệp vụ thật

Khung sinh ra cố tình nghèo nàn: một `Course` chỉ có tên, mô tả và vòng đời DRAFT → ACTIVE → ARCHIVED.
Năm chỗ cần sửa, theo thứ tự này:

1. **`domain/course/model/Course.java`** — thêm dữ liệu và **luật** thật: `language`, `cefrLevel`,
   `lessonCount`, và các quy tắc kiểu "khoá đã ACTIVE thì không đổi ngôn ngữ". Luật nghiệp vụ thuộc về
   domain, không phải controller, vì mọi đường vào (REST, GraphQL, consumer) đều phải chịu cùng một luật.
2. **`domain/course/value/`** — thứ có quy tắc riêng thì tách thành value object: `CefrLevel` chỉ nhận
   A1…C2, `LanguageCode` chuẩn hoá chữ thường. Đừng để chúng là `String`.
3. **`application/course/validation/CourseValidator.java`** — kiểm tra đầu vào, gom hết lỗi rồi trả một
   lần, thay vì chết ở lỗi đầu tiên.
4. **`resources/db/changelog/learning-content/001-course.sql`** — cột tương ứng. Chưa chạy production thì
   sửa thẳng changeset này; đã chạy rồi thì thêm `002-...sql`, không bao giờ sửa changeset cũ.
5. **`web/rest/CourseController.java`** — endpoint còn thiếu. Controller chỉ đọc request, gọi
   use case tương ứng (`createCourse.execute(...)`) và trả về; không chứa `if` nghiệp vụ nào.

---

## 7. Thêm feature thứ hai vào module (việc thường làm nhất)

Ví dụ thêm `lesson` vào `trellis-learning-content`. Lệnh sinh đã tạo sẵn
`domain/lesson/` và `application/lesson/`, nên bạn biết chỗ để đặt:

1. **`domain/lesson/`** — tạo các package con của T17 bên trong: `model/Lesson.java`,
   `value/LessonCode.java`, `policy/` nếu có luật riêng.
2. **`application/lesson/`** — `usecase/<capability>/<Verb>LessonUseCase.java`, `support/`,
   `port/LessonRepository.java`, `view/`, `validation/`. Tên class theo T16: một class một use case, hoặc
   tên use case (`PublishLesson`); `LessonService` bị cấm.
3. **`api/security/CoursePermissions.java`** — thêm `lesson:read`, `lesson:manage` vào cùng
   `@PermissionCatalog` của module, rồi map cho role trong `application-dev.yml` (mục 4).
4. **`api/error/`** — thêm hằng số vào `CourseErrorCode`, hoặc tạo `LessonErrorCode` nếu đã nhiều.
5. **`infrastructure/persistence/JdbcLessonRepository.java`** — cùng thư mục với repository của course,
   không tạo `persistence/lesson/`.
6. **`web/rest/LessonController.java`** và **`web/graphql/`** — cùng thư mục, không chia theo feature.
7. **Changeset mới**: `resources/db/changelog/learning-content/002-lesson.sql`, rồi **tự thêm** dòng
   include vào `trellis-app/src/main/resources/db/changelog/db.changelog-schema.yaml` ở cuối. Lệnh sinh
   chỉ làm việc này cho changeset đầu tiên.
8. `./gradlew build` — ArchUnit sẽ bắt nếu đặt sai tầng.

Không chạm vào `settings.gradle.kts`, `trellis-app/build.gradle.kts` hay `AutoConfiguration.imports`:
feature mới nằm trong module đã được lắp sẵn.

Khi nào một feature đáng tách ra thành module riêng? Khi nó bắt đầu trả lời được năm câu ở mục 1 — thường
là lúc nó có dữ liệu riêng mà module hiện tại không nên đọc, hoặc cần bỏ ra khỏi một deployable.

---

## 8. Khi nào thêm gì

| Cần | Làm ở đâu | Xem mẫu |
| --- | --- | --- |
| Báo cho hệ thống khác biết (gửi mail, cập nhật thống kê) | `api/event/` khai payload, use case gọi outbox trong cùng transaction | `trellis-notification` với `notification.published` |
| Màn hình cần dữ liệu tổng hợp nhiều nguồn | `web/graphql/` + schema của module, dùng `@BatchMapping` để tránh N+1 | `NotificationGraphQlController` |
| Số liệu đọc nhiều, tính nặng | read model riêng: `infrastructure/messaging/` cập nhật theo event, kèm `ReadModelRebuilder` để dựng lại | `NotificationStatsProjection` |
| Phản ứng với event của module khác | `infrastructure/messaging/` với một `EventHandler` | `NotificationStatsProjection` |
| Gọi API bên ngoài | `infrastructure/client/` | chưa có mẫu |

Nguyên tắc: `api/` là thứ **module khác được import**; mọi thứ còn lại là nội bộ.

---

## 9. Lỗi hay gặp

| Triệu chứng | Nguyên nhân |
| --- | --- |
| Mọi request trả 403 | chưa map permission cho role, mục 4 |
| Bảng không tồn tại | quên dòng include trong master changelog (lệnh sinh làm sẵn cho changeset đầu, các changeset sau bạn tự thêm) |
| ArchUnit đỏ | đặt class sai tầng, ví dụ gọi `JdbcClient` từ `application/` |
| ArchUnit không nhận ra module là module nghiệp vụ | package bị lồng (`io.trellis.learning.content`) — phải là một segment |
| Logic nghiệp vụ không chạy khi gọi từ GraphQL | luật nằm trong controller REST thay vì trong domain hoặc application |
| `@Transactional` không có tác dụng | đặt trên method private, hoặc gọi nội bộ qua `this` nên proxy không chạy |
| `@Idempotent` không chặn request lặp | key SpEL trỏ sai field, nên mỗi lần gọi ra một key khác |

---

## 10. Xoá một module

```bash
rm -rf trellis-learning-content
```

rồi gỡ ba chỗ nối: dòng trong `settings.gradle.kts`, dependency trong `trellis-app/build.gradle.kts`, và
dòng include trong master changelog. Nếu đã chạy trên database nào thì cần thêm một changeset xoá bảng.
Chạy `./gradlew build` để xác nhận.
