package io.trellis.app.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.trellis.app.security.PrincipalMigrationFixtures.LegacyFixture;
import io.trellis.app.security.PrincipalMigrationFixtures.MigrationMode;
import io.trellis.app.support.RoleFixtures;
import io.trellis.app.support.SecurityTestContext;
import io.trellis.common.context.Actor;
import io.trellis.common.context.IdentityOrigin;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.NotFoundException;
import io.trellis.common.security.Permission;
import io.trellis.common.security.PlatformPermissions;
import io.trellis.common.security.ResourceScope;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.learningcontent.api.security.CoursePermissions;
import io.trellis.learningcontent.application.course.usecase.reading.GetCourseUseCase;
import io.trellis.learningcontent.application.course.view.CourseView;
import io.trellis.learningcontent.infrastructure.persistence.CourseJpaRepository;
import io.trellis.notice.infrastructure.persistence.NoticeJpaRepository;
import io.trellis.platform.security.authz.GrantSubjectType;
import io.trellis.platform.security.authz.PermissionResolver;
import io.trellis.platform.security.authz.RoleGrant;
import io.trellis.platform.security.authz.RoleGrantFilter;
import io.trellis.platform.security.authz.RoleGrantStore;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

@SecurityTestContext
@Import(PrincipalMigrationFixtures.class)
@RequiredArgsConstructor
class OidcPrincipalMigrationTest {
  private static final String INVALID_MAPPING = "22023";
  private static final String UNIQUE_VIOLATION = "23505";
  private static final String CHECK_VIOLATION = "23514";
  private final PrincipalMigrationFixtures migrations;

  private final UserRepository userRepository;
  private final CourseJpaRepository courseRows;
  private final NoticeJpaRepository noticeRows;
  private final RoleGrantStore roleGrantStore;
  private final PermissionResolver permissionResolver;
  private final GetCourseUseCase getCourseUseCase;
  private final Clock clock;

  @Test
  void shouldMigrateServiceGrantsWithoutInheritingNativeAdminRights() throws Exception {
    // Given
    val fixture = migrations.legacyService();
    val service = Actor.service(fixture.encodedId(), Set.of(), Set.of());
    assertTrue(permissionResolver.permissionsOf(service, ResourceScope.global()).isEmpty());

    // When
    migrations.migrate(fixture, MigrationMode.APPLY);

    // Then
    val permissions = permissionResolver.permissionsOf(service, ResourceScope.global());
    assertTrue(permissions.contains(Permission.of(CoursePermissions.READ)));
    assertFalse(permissions.contains(Permission.of(PlatformPermissions.ROLE_GRANTS_MANAGE)));
    assertEquals(fixture.externalGrant().id(), externalGrant(fixture, fixture.encodedId()).id());
    assertEquals(fixture.nativeGrant(), nativeAdminGrant(fixture));
  }

  @Test
  void shouldRejectSelectedOwnershipThatChangedAfterInventory() throws Exception {
    // Given
    val fixture = migrations.legacyIdentity();
    val changedOwner = UUID.randomUUID().toString();
    migrations.replaceCourseOwner(fixture.externalCourse(), changedOwner);

    // When
    val failure =
        assertThrows(SQLException.class, () -> migrations.migrate(fixture, MigrationMode.APPLY));

    // Then
    assertEquals(INVALID_MAPPING, failure.getSQLState());
    assertEquals(
        changedOwner,
        courseRows.findById(fixture.externalCourse().id()).orElseThrow().getOwnerId());
    assertEquals(fixture.externalGrant(), externalGrant(fixture, fixture.legacyId()));
    assertEquals(
        fixture.legacyId(), noticeRows.findById(fixture.notice().id()).orElseThrow().getAuthorId());
  }

  @Test
  void shouldRejectReverseMigrationThatWouldMergeANewNativeGrant() throws Exception {
    // Given
    val fixture = migrations.legacyIdentity();
    migrations.migrate(fixture, MigrationMode.APPLY);
    val migratedGrant = externalGrant(fixture, fixture.encodedId());
    val newNativeGrant = migrations.grant(fixture.legacyId(), RoleFixtures.READER_ROLE);

    // When
    val failure =
        assertThrows(SQLException.class, () -> migrations.migrate(fixture, MigrationMode.REVERSE));

    // Then
    assertEquals(UNIQUE_VIOLATION, failure.getSQLState());
    assertEquals(migratedGrant, externalGrant(fixture, fixture.encodedId()));
    assertEquals(newNativeGrant, externalGrant(fixture, fixture.legacyId()));
    assertEquals(
        fixture.encodedId(),
        courseRows.findById(fixture.externalCourse().id()).orElseThrow().getOwnerId());
  }

  @Test
  void shouldRestoreExternalGrantsAndOwnershipFromReviewedLegacyRows() throws Exception {
    // Given
    val fixture = migrations.legacyIdentity();
    assertThrows(NotFoundException.class, () -> readExternalCourse(fixture));

    // When
    migrations.migrate(fixture, MigrationMode.APPLY);

    // Then
    assertEquals(fixture.externalCourse().code().value(), readExternalCourse(fixture).code());
    assertEquals(
        fixture.encodedId(),
        courseRows.findById(fixture.externalCourse().id()).orElseThrow().getOwnerId());
    val notice = noticeRows.findById(fixture.notice().id()).orElseThrow();
    assertEquals(fixture.encodedId(), notice.getAuthorId());
    assertEquals(fixture.encodedId(), notice.getPublishedBy());
    val grant = externalGrant(fixture, fixture.encodedId());
    assertEquals(fixture.externalGrant().id(), grant.id());
    assertEquals(fixture.externalGrant().expiresAt(), grant.expiresAt());
    assertEquals(fixture.externalGrant().grantedBy(), grant.grantedBy());
  }

  @Test
  void shouldPreserveNativeBootstrapAdminWhenLegacySubjectMatchesItsUuid() throws Exception {
    // Given
    val fixture = migrations.legacyIdentity();
    val nativeActor = Actor.user(fixture.legacyId(), Set.of(), Set.of());
    val manageGrants = Permission.of(PlatformPermissions.ROLE_GRANTS_MANAGE);
    assertTrue(
        permissionResolver
            .permissionsOf(nativeActor, ResourceScope.global())
            .contains(manageGrants));

    // When
    migrations.migrate(fixture, MigrationMode.APPLY);

    // Then
    assertTrue(userRepository.userById(fixture.nativeUserId()).isPresent());
    assertEquals(
        fixture.legacyId(),
        courseRows.findById(fixture.nativeCourse().id()).orElseThrow().getOwnerId());
    assertEquals(fixture.nativeGrant(), nativeAdminGrant(fixture));
    assertTrue(
        permissionResolver
            .permissionsOf(nativeActor, ResourceScope.global())
            .contains(manageGrants));
    assertFalse(
        permissionResolver
            .permissionsOf(externalActor(fixture), ResourceScope.global())
            .contains(manageGrants));
  }

  @Test
  void shouldRejectUnreviewedProvenanceWithoutChangingLegacyRows() throws Exception {
    // Given
    val fixture = migrations.legacyIdentity();

    // When
    val failure =
        assertThrows(
            SQLException.class, () -> migrations.migrate(fixture, MigrationMode.UNREVIEWED));

    // Then
    assertEquals(INVALID_MAPPING, failure.getSQLState());
    assertEquals(fixture.externalGrant(), externalGrant(fixture, fixture.legacyId()));
    assertEquals(
        fixture.legacyId(),
        courseRows.findById(fixture.externalCourse().id()).orElseThrow().getOwnerId());
  }

  @Test
  void shouldRejectMappingWithoutIssuer() throws Exception {
    // Given
    val fixture = migrations.legacyIdentity();

    // When
    val failure =
        assertThrows(
            SQLException.class, () -> migrations.migrate(fixture, MigrationMode.MISSING_ISSUER));

    // Then
    assertEquals(CHECK_VIOLATION, failure.getSQLState());
    assertEquals(fixture.externalGrant(), externalGrant(fixture, fixture.legacyId()));
  }

  @Test
  void shouldRejectGrantConflictBeforeChangingOwnership() throws Exception {
    // Given
    val fixture = migrations.legacyIdentity();
    val existingTarget = migrations.grant(fixture.encodedId(), RoleFixtures.READER_ROLE);

    // When
    val failure =
        assertThrows(SQLException.class, () -> migrations.migrate(fixture, MigrationMode.APPLY));

    // Then
    assertEquals(UNIQUE_VIOLATION, failure.getSQLState());
    assertEquals(fixture.externalGrant(), externalGrant(fixture, fixture.legacyId()));
    assertEquals(existingTarget, externalGrant(fixture, fixture.encodedId()));
    assertEquals(
        fixture.legacyId(),
        courseRows.findById(fixture.externalCourse().id()).orElseThrow().getOwnerId());
    assertEquals(
        fixture.legacyId(), noticeRows.findById(fixture.notice().id()).orElseThrow().getAuthorId());
  }

  @Test
  void shouldRollbackSuccessfulDryRun() throws Exception {
    // Given
    val fixture = migrations.legacyIdentity();

    // When
    migrations.migrate(fixture, MigrationMode.DRY_RUN);

    // Then
    assertEquals(fixture.externalGrant(), externalGrant(fixture, fixture.legacyId()));
    assertEquals(
        fixture.legacyId(),
        courseRows.findById(fixture.externalCourse().id()).orElseThrow().getOwnerId());
    assertEquals(
        fixture.legacyId(),
        noticeRows.findById(fixture.notice().id()).orElseThrow().getPublishedBy());
    assertThrows(NotFoundException.class, () -> readExternalCourse(fixture));
  }

  @Test
  void shouldKeepGrantIdentityAndMetadataWhenReviewedMappingIsReplayed() throws Exception {
    // Given
    val fixture = migrations.legacyIdentity();
    migrations.migrate(fixture, MigrationMode.APPLY);
    val migratedGrant = externalGrant(fixture, fixture.encodedId());

    // When
    migrations.migrate(fixture, MigrationMode.APPLY);

    // Then
    assertEquals(migratedGrant, externalGrant(fixture, fixture.encodedId()));
    assertEquals(
        fixture.encodedId(),
        courseRows.findById(fixture.externalCourse().id()).orElseThrow().getOwnerId());
    assertEquals(fixture.nativeGrant(), nativeAdminGrant(fixture));
  }

  @Test
  void shouldRestoreSelectedLegacyRowsWhenReversingReviewedMapping() throws Exception {
    // Given
    val fixture = migrations.legacyIdentity();
    migrations.migrate(fixture, MigrationMode.APPLY);

    // When
    migrations.migrate(fixture, MigrationMode.REVERSE);

    // Then
    assertEquals(fixture.externalGrant(), externalGrant(fixture, fixture.legacyId()));
    assertEquals(
        fixture.legacyId(),
        courseRows.findById(fixture.externalCourse().id()).orElseThrow().getOwnerId());
    val notice = noticeRows.findById(fixture.notice().id()).orElseThrow();
    assertEquals(fixture.legacyId(), notice.getAuthorId());
    assertEquals(fixture.legacyId(), notice.getPublishedBy());
    assertEquals(fixture.nativeGrant(), nativeAdminGrant(fixture));
    assertThrows(NotFoundException.class, () -> readExternalCourse(fixture));
  }

  private CourseView readExternalCourse(final LegacyFixture fixture) {
    val context =
        RequestContext.builder()
            .actor(externalActor(fixture))
            .correlationId(UUID.randomUUID().toString())
            .requestTime(clock.instant())
            .build();
    return getCourseUseCase.execute(context, fixture.externalCourse().code().value());
  }

  private Actor externalActor(final LegacyFixture fixture) {
    return Actor.user(fixture.encodedId(), Set.of(), Set.of(), IdentityOrigin.OIDC);
  }

  private RoleGrant externalGrant(final LegacyFixture fixture, final String subjectId) {
    return roleGrantStore
        .page(
            new RoleGrantFilter(
                fixture.externalGrant().subjectType(),
                subjectId,
                fixture.externalGrant().role(),
                ResourceScope.GLOBAL,
                ResourceScope.GLOBAL),
            null,
            50)
        .items()
        .getFirst();
  }

  private RoleGrant nativeAdminGrant(final LegacyFixture fixture) {
    return roleGrantStore
        .page(
            new RoleGrantFilter(
                GrantSubjectType.USER,
                fixture.legacyId(),
                RoleFixtures.ADMINISTRATOR_ROLE,
                ResourceScope.GLOBAL,
                ResourceScope.GLOBAL),
            null,
            50)
        .items()
        .getFirst();
  }
}
