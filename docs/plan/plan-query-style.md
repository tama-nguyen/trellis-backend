# Plan: chuyển persistence sang JPA và chuẩn hoá query

Trạng thái: **đã thực thi** (xem mục 0b). Chuẩn tham chiếu: `JPAGroupRepository` của
`ipos-identity-service`. Quyết định của chủ repo: **dùng JPA**, vì nó tốt nhất cho đường dài.

---

## 0b. Đã làm những gì

| Bước | Trạng thái |
| --- | --- |
| ADR 0004 đảo T10 | xong - `../adr/0004-persistence-jpa.md` |
| Hạ tầng: starter, `PersistenceConfiguration`, 4 thuộc tính ở 3 profile | xong |
| `ArchitectureTest.noJpa()` → `jpaStaysInPersistenceAdapters()` | xong |
| `trellis-identity`: 4 entity, 4 repository native, adapter | xong - `JdbcAccountRepository` đã xoá |
| `trellis-notification`: `NotificationEntity`, native query, keyset qua `EntityManager` | xong |
| `trellis-learning-content`: `CourseEntity`, tương tự, giữ `GrantPredicate` | xong |
| Xoá toàn bộ `package-info.java` (102 file) + sửa generator và docs | xong |
| Bước gắn data source cho IDE trong `running.md` | xong |
| Platform (outbox, inbox, idempotency, audit, role_grant) | giữ `JdbcClient` như kế hoạch |

Hai điều chỉnh so với kế hoạch, do thực tế khi làm:

1. **Mọi `@Query` là `nativeQuery = true`**, không dùng JPQL (yêu cầu của chủ repo giữa chừng). Kéo theo:
   `@Lock` không áp được cho native query nên khoá viết thẳng `FOR UPDATE` trong câu lệnh; enum bind bằng
   `.name()`; và mọi `@Modifying` có `flushAutomatically`/`clearAutomatically`.
2. **Trang keyset chạy qua `EntityManager.createNativeQuery(sql, XEntity.class)`** thay vì giữ `JdbcClient`.
   Giữ `JdbcClient` sẽ phải giữ thêm một `RowMapper` song song với entity - hai lớp mapping cho cùng một
   bảng. Cách này chỉ còn một.

---

## 0. Trước hết: 173 warning không phải do thiếu entity

Cần tách bạch, vì nếu nhầm nguyên nhân thì chuyển sang JPA xong vẫn còn nguyên warning.

`Unable to resolve table 'account'` là inspection của **Database Tools** trong IntelliJ. Nó chỉ phân
giải được tên bảng khi IDE có **data source**. Kiểm tra:

```
.idea/dataSources.xml                      → KHÔNG có (trellis-backend)
/Users/tam/Documents/ipos/be/.idea/...     → cũng KHÔNG có
```

Và `JPAGroupRepository` - file được lấy làm chuẩn - có **3 khối `nativeQuery = true`**. Ba khối đó
warning y hệt trong chính project ipos, vì IDE ở đó cũng không có data source. JPA chỉ phân giải
được **JPQL** (dựa vào entity); **native SQL vẫn cần data source**. Thậm chí
`@Table(name = "account")` cũng bị chính inspection đó gắn cờ khi không có data source - nghĩa là
thêm entity có thể làm **tăng** số warning, không giảm.

**Cách sửa thật**, làm ngay, độc lập với JPA:

1. **Database → + → Data Source → PostgreSQL**, trỏ `localhost:15432`, user/pass `trellis`
   (stack local đã chạy sẵn), hoặc
2. **Data Source from DDL**: trỏ vào `*/src/main/resources/db/changelog/**/*.sql` - chạy offline,
   không cần container, và luôn khớp với schema trong repo.

`../../.idea` nằm trong `.gitignore` nên không commit được cấu hình này; nó phải thành **một bước trong
`docs/guide/running.md`**, nếu không mỗi người mới vào dự án lại gặp đúng 173 warning này.

Một đính chính nhỏ về cú pháp: không có `@Entity(table = "...")`. Đúng là `@Entity` (đánh dấu lớp)
cộng `@Table(name = "...")` (đặt tên bảng) - hai annotation khác nhau, và plan dưới đây dùng cả hai
tường minh.

---

## 1. Quyết định: JPA, và nó đảo decision T10

File tham khảo là **Spring Data JPA**: `extends JpaRepository<GroupEntity, Long>`, `@Query` JPQL,
`@Param`, projection interface, derived query method.

Repo này đang cấm JPA, không phải cấm ngầm:

- `../../AGENTS.md`: *"PostgreSQL 17 via `JdbcClient` (no JPA)"*, *"No JPA/Hibernate (ArchUnit)"*.
- `ArchitectureTest.noJpa()` fail build nếu chạm `jakarta.persistence..` hoặc `org.hibernate..`,
  lý do ghi trong test: *"decision T10"*.

Nên việc này cần **ADR 0004 đảo T10**, không phải một PR style. T10 hiện **không có ADR nào** (chỉ
có một dòng `.because(...)` trong test), nên ADR mới sẽ là chỗ đầu tiên ghi lại cả hai phía của lựa
chọn.

---

## 2. Vì sao code hiện tại khó đọc, đo được

`JdbcAccountRepository`: **317 dòng cho 18 method**, trong đó chỉ ~110 dòng là SQL. Phần còn lại là
`jdbc.sql(X).param(...).param(...).query(MAPPER).optional()` lặp lại cộng `RowMapper` viết tay.
Tổng 10 repository/store: **1.287 dòng**.

Thêm bốn chỗ ghép SQL bằng `StringBuilder` (`JdbcCourseRepository.page`, `JdbcNotificationRepository.page`,
`JdbcAuditEventStore.page`, `JdbcRoleGrantStore.page`): muốn biết câu SQL cuối cùng phải đọc ba
nhánh `if` rồi tự nối trong đầu.

Đó là hai vấn đề khác nhau: **boilerplate** (JPA giải quyết) và **query ghép động** (JPA không giải
quyết - xem mục 4).

---

## 3. Query tĩnh: phần đúng của file tham khảo, áp cho cả hai bên

Phần này áp dụng **bất kể** chọn JPA hay không: nó là cách viết, không phải công nghệ.

### 3.1. Mọi query thành hằng tĩnh; filter tuỳ chọn dùng dạng null-guard

```java
private static final String PAGE = """
        SELECT id, code, name, status, owner_id, version, created_at, updated_at
          FROM course
         WHERE (:status IS NULL OR status = :status)
           AND (:namePattern IS NULL OR name ILIKE :namePattern ESCAPE '\\')
        """;
```

thay cho `StringBuilder` + `if (filter.status() != null) sql.append(STATUS_IS)`.

Trong JPA đây là `@Query` + `@Param`; trong phần còn giữ `JdbcClient` thì named parameter bind y hệt.
Cùng một luật cho cả hai bên.

### 3.2. Nhưng **không** áp cho đúng hai chỗ, và đây là lý do có số đo

Dạng null-guard có một cái bẫy. Đo trên bảng 200k dòng, có index `(status)`:

| Cách | Plan | Thời gian |
| --- | --- | --- |
| Ghép chuỗi (`AND status = :status`) | Index Scan | **3.98 ms** |
| Null-guard, **custom plan** (mặc định) | Index Scan | **3.44 ms** |
| Null-guard, **generic plan** | Parallel Seq Scan, loại 98k dòng bằng Filter | **10.81 ms** |

Hai dòng đầu bằng nhau: Postgres rút gọn `$1 IS NULL` thành false khi biết giá trị tham số, nên
null-guard **không** mất index. Dòng thứ ba là cái bẫy: khi statement chuyển sang **generic plan**,
`$1 IS NULL` không rút gọn được nữa và index biến mất. pgjdbc mặc định `prepareThreshold=5`, nên
một query chạy nhiều lần có thể rơi vào đúng trạng thái đó, và khoảng cách này giãn ra theo kích
thước bảng.

Nên: **null-guard là mặc định** cho mọi query, trừ query danh sách trên bảng lớn có filter chọn lọc
cao. Với nhóm đó, hoặc giữ ghép chuỗi (và nói rõ vì sao trong comment), hoặc đặt
`prepareThreshold=0` cho datasource để Postgres luôn plan theo tham số thật. Quyết định này cần đo
lại trên từng bảng, không suy từ bảng khác.

### 3.3. Đặt tên method theo ý định

`page(filter, after, limit)` → `pageVisibleTo(...)`, `findByCode`, `pageByAggregate(...)`. Tên hiện
tại mô tả *kỹ thuật phân trang*, không mô tả *câu hỏi nghiệp vụ*.

### 3.4. Thống nhất format SQL

Chữ hoa cho từ khoá (đang theo chuẩn này), xuống dòng theo mệnh đề, cột liệt kê tường minh —
đã làm đúng ở hầu hết chỗ; chuẩn hoá nốt vài text block một dòng.

---

## 4. Năm chỗ JPA không phủ, và cách xử lý từng chỗ

Đây không phải lý do để không làm, mà là danh sách việc phải quyết trong lúc làm.

| Chỗ | Vấn đề | Cách xử lý trong plan này |
| --- | --- | --- |
| **Phân trang keyset** (`KeysetQuery` + cursor ký) | Spring Data phân trang theo offset; keyset cần predicate động theo cursor | 4 method `page` **giữ `JdbcClient`**. Offset phân trang sai khi dữ liệu chèn giữa chừng, không đổi được |
| **`GrantPredicate`** | Đẩy mệnh đề SQL của platform vào query của module khác; JPQL không ghép fragment | **Giữ `JdbcClient`** cho query danh sách có lọc theo grant |
| **Domain là `record` bất biến** | `@Entity` cần class mutable, constructor `protected` rỗng; `businessDomainIsFrameworkFree` cấm `jakarta..` trong `domain/` | Entity là **lớp riêng** ở `infrastructure/persistence/entity`, kèm mapper sang/từ record. Domain không đổi, ArchUnit vẫn pass |
| **6 bảng platform** (outbox, inbox, idempotency, audit, role_grant, migration checkpoint) | Không có aggregate nghiệp vụ tương ứng; thao tác theo lô và theo khoá phiên | **Giữ `JdbcClient`**. JPA hoá chúng không được gì |
| **Khoá bi quan** (`SELECT ... FOR UPDATE`) | Có trong `lockActiveRefreshSession`, `lockActiveActionToken` | JPA có sẵn: `@Lock(LockModeType.PESSIMISTIC_WRITE)` trên method repository. Đây là chỗ JPA **hơn** code hiện tại |

Kết quả là một kiến trúc lai, và điều đó phải được ghi thành luật (mục 7), nếu không mỗi người chọn
một kiểu: **JPA cho vòng đời aggregate nghiệp vụ; `JdbcClient` cho keyset, grant pushdown và store
của platform.**

---

## 5. Hình dạng code đích

### 5.1. Entity

```java
// io.trellis.identity.infrastructure.persistence.entity
@Entity
@Table(name = "account")
public class AccountEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AccountEntity() {} // JPA
}
```

Convention bắt buộc, vì thiếu nó là nguyên nhân thật của "thiếu convention":

- Tên lớp `<Aggregate>Entity`, **luôn** có `@Table(name = ...)` tường minh.
- **Luôn** có `@Column(name = ...)` tường minh - không dựa vào naming strategy ngầm, để tên cột
  trong Java và trong changeset khớp nhau bằng mắt.
- `@Enumerated(EnumType.STRING)` - không bao giờ ORDINAL.
- Không có quan hệ `@OneToMany`/`@ManyToOne` giữa các aggregate: nối bằng id. Schema đã bỏ FK, hai
  quyết định khớp nhau.

### 5.2. Repository

```java
public interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID> {

    Optional<AccountEntity> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM AccountSessionEntity s
             WHERE s.refreshTokenHash = :tokenHash
               AND s.revokedAt IS NULL
               AND s.refreshExpiresAt > :now
            """)
    Optional<AccountSessionEntity> lockActiveRefreshSession(
            @Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE AccountEntity a SET a.emailVerifiedAt = :at, a.status = 'ACTIVE' WHERE a.id = :id")
    int verifyEmail(@Param("id") UUID id, @Param("at") Instant at);
}
```

JPQL khi được; `nativeQuery = true` chỉ khi JPQL không diễn đạt nổi, và khi đó **phải có comment nói
vì sao** - để lần sau không ai tưởng là tuỳ hứng.

### 5.3. Cấu hình bắt buộc

| Thuộc tính | Giá trị | Vì sao |
| --- | --- | --- |
| `spring.jpa.open-in-view` | `false` | Boot mặc định `true` và cảnh báo lúc khởi động. Để `true` nghĩa là session mở suốt request, lazy loading nổ ở tầng web - đúng thứ `businessWebStaysThin` muốn chặn |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Liquibase sở hữu schema. `validate` bắt lệch entity ↔ bảng **lúc khởi động** - đây mới là thứ thật sự chặn được lỗi "entity sai tên bảng", chứ không phải inspection của IDE |
| `spring.jpa.properties.hibernate.jdbc.batch_size` | `50` | Gom insert/update theo lô |
| `spring.jpa.properties.hibernate.order_inserts` / `order_updates` | `true` | Để batch có tác dụng |

---

## 6. Kế hoạch thực thi

**Bước 0 - ADR 0004** đảo T10: ghi ranh giới ở mục 4, convention ở 5.1, cấu hình ở 5.3, và cả lý do
giữ `JdbcClient` ở bốn chỗ. Không code.

**Bước 1 - hạ tầng, không đổi hành vi**: thêm `spring-boot-starter-data-jpa`; `@EntityScan` và
`@EnableJpaRepositories` giới hạn trong `..infrastructure.persistence..`; đặt 4 thuộc tính ở 5.3;
đổi `ArchitectureTest.noJpa()` thành rule phạm vi: `jakarta.persistence..` và `org.hibernate..` chỉ
được xuất hiện trong `..infrastructure.persistence..`.

**Bước 2 - `trellis-identity` làm mẫu** (317 dòng, đau nhất): 4 entity (`account`,
`account_password`, `account_session`, `account_action_token`), 3 repository JPA, mapper sang/từ
record domain. Hai method `FOR UPDATE` chuyển sang `@Lock(PESSIMISTIC_WRITE)`. Đo lại số dòng.

**Bước 3 - `trellis-notification`, `trellis-learning-content`**: `findByCode`, `save` qua JPA; `page`
**giữ `JdbcClient`** + `KeysetQuery` + `GrantPredicate`.

**Bước 4 - platform giữ nguyên hoàn toàn**: outbox, inbox, idempotency, audit, role_grant.

**Bước 5 - áp mục 3 cho phần `JdbcClient` còn lại**: query tĩnh, null-guard, kèm cảnh báo generic
plan đã đo ở 3.2.

**Bước 6 - IDE data source** (mục 0) thành một bước trong `../guide/running.md`.

**Bước 7 - rules và skills** (mục 7).

---

## 7. Rules và skills phải sửa theo

| File | Sửa gì |
| --- | --- |
| `../../AGENTS.md` mục "Code standards (Java)" | Thêm: query là **một hằng text block tĩnh**; filter tuỳ chọn dùng `(:x IS NULL OR col = :x)`; ghép chuỗi chỉ khi số mệnh đề thay đổi theo caller, và phải có comment nói vì sao |
| `../../AGENTS.md` mục "How to add..." | Thêm dòng về đặt tên method repository theo câu hỏi nghiệp vụ |
| `../../.agent-skills/review/standards/java.md` | Thêm mục kiểm tra: `new StringBuilder` trong repository là dấu hiệu cần giải thích; query không grep được là lỗi |
| `../architecture/backend.md` §7 (pattern catalogue) | Ghi dạng null-guard + cái bẫy generic plan kèm số đo ở 3.2 |
| `ArchitectureTest.noJpa()` | Đổi từ "cấm hoàn toàn" sang "chỉ cho phép trong `..infrastructure.persistence..`" - vẫn chặn được entity rò ra `domain/` hay `web/` |
| `../adr/0004-persistence-jpa.md` | Mới, **đảo T10**: vì sao JPA, ranh giới JPA ↔ `JdbcClient` (mục 4), convention entity (5.1), cấu hình bắt buộc (5.3) |
| `../guide/running.md` | Thêm bước gắn data source cho IDE (mục 0) |
| `../../AGENTS.md` mục "Stack" | `PostgreSQL 17 via JdbcClient (no JPA)` → JPA cho aggregate nghiệp vụ, `JdbcClient` cho keyset/grant/platform |

---

## 8. Điểm cần chủ repo quyết

1. **Phạm vi lai.** Plan này giữ `JdbcClient` ở 4 chỗ (mục 4). Nếu bạn muốn JPA **tuyệt đối**, hai
   chỗ đầu phải viết bằng `nativeQuery = true` + tự ghép cursor - tức là quay lại đúng kiểu code bạn
   đang chê, chỉ khác là nằm trong annotation. Tôi đề nghị giữ lai và ghi rõ ranh giới.
2. **`ddl-auto=validate`** sẽ làm app **không khởi động được** nếu entity lệch bảng. Đó là chủ ý,
   nhưng cần bạn xác nhận vì nó chặn cả môi trường đang chạy.
3. **Đổi tên method repository** theo ý định (3.3) kéo theo đổi `application/port` và chỗ gọi.
