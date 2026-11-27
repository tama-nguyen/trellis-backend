package io.trellis.app.security;

import static io.trellis.app.support.TestJwtIssuer.ISSUER;

import io.trellis.app.support.IdentityFixtures;
import io.trellis.app.support.RoleFixtures;
import io.trellis.common.id.ReferenceCodeGenerator;
import io.trellis.common.security.ResourceScope;
import io.trellis.identity.domain.user.UserStatus;
import io.trellis.learningcontent.application.course.port.CourseRepository;
import io.trellis.learningcontent.domain.course.model.Course;
import io.trellis.learningcontent.domain.course.model.CourseDetails;
import io.trellis.learningcontent.domain.course.value.CourseCode;
import io.trellis.learningcontent.infrastructure.persistence.CourseJpaRepository;
import io.trellis.notification.api.dto.NotificationAudience;
import io.trellis.notification.api.dto.NotificationSeverity;
import io.trellis.notification.application.port.NotificationRepository;
import io.trellis.notification.domain.model.Notification;
import io.trellis.notification.domain.model.NotificationContent;
import io.trellis.notification.domain.value.NotificationCode;
import io.trellis.platform.security.authz.GrantSubjectType;
import io.trellis.platform.security.authz.RoleGrant;
import io.trellis.platform.security.authz.RoleGrantStore;
import io.trellis.platform.security.jwt.OidcPrincipalIdEncoder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.core.io.ClassPathResource;

/** Builds legacy state with repositories and executes the real operator mapping protocol. */
@RequiredArgsConstructor
class PrincipalMigrationFixtures {
  private static final String PREPARE = "db/operations/identity/prepare-principal-mapping.sql";
  private static final String APPLY = "db/operations/identity/apply-principal-mapping.sql";
  private static final String INSERT_MAPPING =
      """
      INSERT INTO principal_identity_map
          (mapping_key, subject_type, legacy_id, issuer, external_subject, encoded_id, provenance_reviewed)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      """;
  private static final String SELECT_GRANT =
      "INSERT INTO principal_grant_map (grant_id, mapping_key) VALUES (?, ?)";
  private static final String SELECT_COURSE =
      "INSERT INTO principal_course_map (course_id, mapping_key) VALUES (?, ?)";
  private static final String SELECT_NOTIFICATION =
      """
      INSERT INTO principal_notification_map (notification_id, author_mapping_key, publisher_mapping_key)
      VALUES (?, ?, ?)
      """;
  private static final String REVERSE = "UPDATE principal_migration_direction SET reverse = TRUE";

  private final IdentityFixtures identities;
  private final CourseRepository courseRepository;
  private final CourseJpaRepository courseRows;
  private final NotificationRepository notificationRepository;
  private final RoleGrantStore roleGrantStore;
  private final DataSource dataSource;
  private final Clock clock;

  LegacyFixture legacyIdentity() {
    val nativeUserId = identities.nativeUser(UserStatus.ACTIVE);
    val legacyId = nativeUserId.toString();
    val externalCourse = createCourse(legacyId);
    val nativeCourse = createCourse(legacyId);
    val now = clock.instant();
    val notification =
        Notification.draft(
                UUID.randomUUID(),
                NotificationCode.generate(new ReferenceCodeGenerator(NotificationCode.PREFIX)),
                new NotificationContent(
                    "Legacy notification",
                    "Reviewed ownership",
                    NotificationSeverity.INFO,
                    NotificationAudience.ALL),
                legacyId,
                now)
            .publish(legacyId, now);
    identities.tx().executeWithoutResult(status -> notificationRepository.insert(notification));
    val externalGrant = grant(legacyId, RoleFixtures.READER_ROLE);
    val nativeGrant = grant(legacyId, RoleFixtures.ADMINISTRATOR_ROLE);
    return new LegacyFixture(
        nativeUserId,
        OidcPrincipalIdEncoder.userId(ISSUER, legacyId),
        externalCourse,
        nativeCourse,
        notification,
        externalGrant,
        nativeGrant);
  }

  private Course createCourse(final String ownerId) {
    val course =
        Course.draft(
            UUID.randomUUID(),
            CourseCode.generate(new ReferenceCodeGenerator(CourseCode.PREFIX)),
            new CourseDetails("Legacy course", "Reviewed owner mapping"),
            ownerId,
            clock.instant());
    identities.tx().executeWithoutResult(status -> courseRepository.insert(course));
    return course;
  }

  LegacyFixture legacyService() {
    val nativeFixture = legacyIdentity();
    val now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    val serviceGrant =
        identities
            .tx()
            .execute(
                status ->
                    roleGrantStore.grant(
                        GrantSubjectType.SERVICE,
                        nativeFixture.legacyId(),
                        RoleFixtures.READER_ROLE,
                        ResourceScope.global(),
                        now,
                        "legacy-service-fixture",
                        now.plus(Duration.ofHours(1))));
    return new LegacyFixture(
        nativeFixture.nativeUserId(),
        OidcPrincipalIdEncoder.serviceId(ISSUER, nativeFixture.legacyId()),
        nativeFixture.externalCourse(),
        nativeFixture.nativeCourse(),
        nativeFixture.notification(),
        serviceGrant,
        nativeFixture.nativeGrant());
  }

  void replaceCourseOwner(final Course course, final String ownerId) {
    identities
        .tx()
        .executeWithoutResult(
            status -> {
              val stored = courseRows.findById(course.id()).orElseThrow();
              stored.setOwnerId(ownerId);
              courseRows.saveAndFlush(stored);
            });
  }

  RoleGrant grant(final String subjectId, final String role) {
    val now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    return identities
        .tx()
        .execute(
            status ->
                roleGrantStore.grant(
                    GrantSubjectType.USER,
                    subjectId,
                    role,
                    ResourceScope.global(),
                    now,
                    "legacy-fixture",
                    now.plus(Duration.ofHours(1))));
  }

  void migrate(final LegacyFixture fixture, final MigrationMode mode)
      throws SQLException, IOException {
    try (val connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        executeScript(connection, PREPARE);
        stageReviewedRows(connection, fixture, mode);
        if (mode == MigrationMode.REVERSE) {
          try (val statement = connection.createStatement()) {
            statement.executeUpdate(REVERSE);
          }
        }
        executeScript(connection, APPLY);
        if (mode == MigrationMode.DRY_RUN) {
          connection.rollback();
        } else {
          connection.commit();
        }
      } catch (SQLException | IOException | RuntimeException failure) {
        connection.rollback();
        throw failure;
      }
    }
  }

  // Operator staging has no repository API; execute the same manifest tables/scripts as the
  // runbook.
  private void stageReviewedRows(
      final Connection connection, final LegacyFixture fixture, final MigrationMode mode)
      throws SQLException {
    val mappingKey = UUID.randomUUID();
    try (val statement = connection.prepareStatement(INSERT_MAPPING)) {
      statement.setObject(1, mappingKey);
      statement.setString(2, fixture.externalGrant().subjectType().name());
      statement.setString(3, fixture.legacyId());
      statement.setString(4, mode == MigrationMode.MISSING_ISSUER ? "" : ISSUER);
      statement.setString(5, fixture.legacyId());
      statement.setString(6, fixture.encodedId());
      statement.setBoolean(7, mode != MigrationMode.UNREVIEWED);
      statement.executeUpdate();
    }
    try (val statement = connection.prepareStatement(SELECT_GRANT)) {
      statement.setLong(1, fixture.externalGrant().id());
      statement.setObject(2, mappingKey);
      statement.executeUpdate();
    }
    try (val statement = connection.prepareStatement(SELECT_COURSE)) {
      statement.setObject(1, fixture.externalCourse().id());
      statement.setObject(2, mappingKey);
      statement.executeUpdate();
    }
    try (val statement = connection.prepareStatement(SELECT_NOTIFICATION)) {
      statement.setObject(1, fixture.notification().id());
      statement.setObject(2, mappingKey);
      statement.setObject(3, mappingKey);
      statement.executeUpdate();
    }
  }

  private void executeScript(final Connection connection, final String resourcePath)
      throws SQLException, IOException {
    val sql = new ClassPathResource(resourcePath).getContentAsString(StandardCharsets.UTF_8);
    try (val statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  enum MigrationMode {
    APPLY,
    DRY_RUN,
    REVERSE,
    UNREVIEWED,
    MISSING_ISSUER
  }

  record LegacyFixture(
      UUID nativeUserId,
      String encodedId,
      Course externalCourse,
      Course nativeCourse,
      Notification notification,
      RoleGrant externalGrant,
      RoleGrant nativeGrant) {
    String legacyId() {
      return nativeUserId.toString();
    }
  }
}
