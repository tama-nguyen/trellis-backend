package io.trellis.app.security;

import static io.trellis.app.support.TestJwtIssuer.ISSUER;
import static io.trellis.app.support.TestJwtIssuer.jwt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.trellis.app.platform.dto.rolegrant.RoleGrantRequest;
import io.trellis.app.support.AuditProbe;
import io.trellis.app.support.IdentityFixtures;
import io.trellis.app.support.RoleFixtures;
import io.trellis.app.support.SecurityTestContext;
import io.trellis.common.id.ReferenceCodeGenerator;
import io.trellis.common.security.ResourceScope;
import io.trellis.identity.domain.user.UserStatus;
import io.trellis.identity.infrastructure.persistence.user.UserJpaRepository;
import io.trellis.learningcontent.api.security.CourseScopes;
import io.trellis.learningcontent.application.course.port.CourseRepository;
import io.trellis.learningcontent.domain.course.model.Course;
import io.trellis.learningcontent.domain.course.model.CourseDetails;
import io.trellis.learningcontent.domain.course.value.CourseCode;
import io.trellis.platform.audit.AuditEventFilter;
import io.trellis.platform.audit.AuditEventStore;
import io.trellis.platform.security.authz.GrantSubjectType;
import io.trellis.platform.security.authz.RoleGrantFilter;
import io.trellis.platform.security.authz.RoleGrantStore;
import io.trellis.platform.security.jwt.OidcPrincipalIdEncoder;
import java.time.Clock;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

@SecurityTestContext
@Import(RoleGrantApi.class)
@RequiredArgsConstructor
class RoleGrantTargetValidationTest {
  @LocalServerPort private int port;

  private final RoleGrantApi api;
  private final IdentityFixtures identities;
  private final UserJpaRepository nativeUsers;
  private final RoleGrantStore roleGrantStore;
  private final CourseRepository courseRepository;
  private final JsonMapper jsonMapper;
  private final Clock clock;
  private final AuditProbe auditProbe;
  private final AuditEventStore auditEventStore;

  @Test
  void shouldRecordRejectedTargetWithoutSuccessfulGrantAudit() throws Exception {
    // Given
    val request = globalRequest(GrantSubjectType.USER, Long.MAX_VALUE + "");

    // When
    val response = api.grant(port, administrator(), UUID.randomUUID().toString(), request);

    // Then
    assertEquals(HttpStatus.NOT_FOUND.value(), response.statusCode());
    val correlationId = response.headers().firstValue("X-Correlation-Id").orElseThrow();
    val rejection = auditProbe.securityEvent(correlationId);
    assertEquals("REJECTED", rejection.get("outcome"));
    assertEquals("GRANT_SUBJECT_NOT_FOUND", rejection.get("error_code"));
    val successes =
        auditEventStore.page(
            new AuditEventFilter(null, null, "platform.role-grant.granted", null, null), null, 50);
    assertTrue(
        successes.items().stream().noneMatch(event -> correlationId.equals(event.correlationId())));
  }

  @Test
  void shouldRejectMissingNativeSubjectWithoutPersistingGrant() throws Exception {
    // Given
    val request = globalRequest(GrantSubjectType.USER, Long.MAX_VALUE + "");

    // When
    val response = api.grant(port, administrator(), UUID.randomUUID().toString(), request);

    // Then
    assertEquals(HttpStatus.NOT_FOUND.value(), response.statusCode());
    assertEquals(
        "GRANT_SUBJECT_NOT_FOUND", jsonMapper.readTree(response.body()).path("code").asString());
    assertTrue(roleGrantStore.page(filter(request), null, 50).items().isEmpty());
  }

  @ParameterizedTest
  @EnumSource(UserStatus.class)
  void shouldAllowExistingNativeSubjectRegardlessOfAccountStatus(final UserStatus status)
      throws Exception {
    // Given
    val subject = identities.nativeUser(status);
    val request = globalRequest(GrantSubjectType.USER, subject.toString());

    // When
    val response = api.grant(port, administrator(), UUID.randomUUID().toString(), request);

    // Then
    assertEquals(HttpStatus.CREATED.value(), response.statusCode());
    assertEquals(subject.toString(), api.body(response).subjectId());
    assertEquals(1, roleGrantStore.page(filter(request), null, 50).items().size());
  }

  @ParameterizedTest
  @EnumSource(GrantSubjectType.class)
  void shouldAllowEncodedExternalSubjectWithoutCreatingNativeUser(final GrantSubjectType type)
      throws Exception {
    // Given
    val subject = externalId(type);
    val request = globalRequest(type, subject);
    val nativeCount = nativeUsers.count();

    // When
    val response = api.grant(port, administrator(), UUID.randomUUID().toString(), request);

    // Then
    assertEquals(HttpStatus.CREATED.value(), response.statusCode());
    assertEquals(subject, api.body(response).subjectId());
    assertEquals(nativeCount, nativeUsers.count());
    assertEquals(1, roleGrantStore.page(filter(request), null, 50).items().size());
  }

  @ParameterizedTest
  @MethodSource("invalidSubjects")
  void shouldRejectMalformedOrTypeMismatchedSubject(
      final GrantSubjectType type, final String subject) throws Exception {
    // Given
    val request = globalRequest(type, subject);

    // When
    val response = api.grant(port, administrator(), UUID.randomUUID().toString(), request);

    // Then
    assertEquals(HttpStatus.BAD_REQUEST.value(), response.statusCode());
    assertEquals(
        "INVALID_GRANT_SUBJECT", jsonMapper.readTree(response.body()).path("code").asString());
    assertTrue(roleGrantStore.page(filter(request), null, 50).items().isEmpty());
  }

  @Test
  void shouldAllowGrantForExistingCourse() throws Exception {
    // Given
    val course = createCourse();
    val request = courseRequest(course.id().toString());

    // When
    val response = api.grant(port, administrator(), UUID.randomUUID().toString(), request);

    // Then
    assertEquals(HttpStatus.CREATED.value(), response.statusCode());
    assertEquals(CourseScopes.COURSE, api.body(response).scopeType());
    assertEquals(course.id().toString(), api.body(response).scopeId());
    assertEquals(1, roleGrantStore.page(filter(request), null, 50).items().size());
  }

  @Test
  void shouldRejectMissingCourseWithoutPersistingGrant() throws Exception {
    // Given
    val request = courseRequest(Long.MAX_VALUE + "");

    // When
    val response = api.grant(port, administrator(), UUID.randomUUID().toString(), request);

    // Then
    assertEquals(HttpStatus.NOT_FOUND.value(), response.statusCode());
    assertEquals(
        "GRANT_RESOURCE_NOT_FOUND", jsonMapper.readTree(response.body()).path("code").asString());
    assertTrue(roleGrantStore.page(filter(request), null, 50).items().isEmpty());
  }

  @ParameterizedTest
  @MethodSource("invalidScopes")
  void shouldRejectUnsupportedOrPartialGlobalScope(final String scopeType, final String scopeId)
      throws Exception {
    // Given
    val request =
        new RoleGrantRequest(
            GrantSubjectType.USER,
            externalId(GrantSubjectType.USER),
            RoleFixtures.READER_ROLE,
            scopeType,
            scopeId,
            null);

    // When
    val response = api.grant(port, administrator(), UUID.randomUUID().toString(), request);

    // Then
    assertEquals(HttpStatus.BAD_REQUEST.value(), response.statusCode());
    assertEquals(
        "INVALID_GRANT_SCOPE", jsonMapper.readTree(response.body()).path("code").asString());
    assertTrue(roleGrantStore.page(filter(request), null, 50).items().isEmpty());
  }

  @Test
  void shouldAuthorizeCallerBeforeCheckingTargetExistence() throws Exception {
    // Given
    val request = globalRequest(GrantSubjectType.USER, UUID.randomUUID().toString());
    val reader = jwt(UUID.randomUUID().toString(), RoleFixtures.READER_ROLE);

    // When
    val response = api.grant(port, reader, UUID.randomUUID().toString(), request);

    // Then
    assertEquals(HttpStatus.FORBIDDEN.value(), response.statusCode());
    assertTrue(roleGrantStore.page(filter(request), null, 50).items().isEmpty());
  }

  private Course createCourse() {
    val draft =
        Course.draft(
            CourseCode.generate(new ReferenceCodeGenerator(CourseCode.PREFIX)),
            new CourseDetails("Grant target", "Existing course resource"),
            "fixture-owner",
            clock.instant());
    return identities.tx().execute(status -> courseRepository.create(draft));
  }

  private RoleGrantRequest courseRequest(final String resourceId) {
    return new RoleGrantRequest(
        GrantSubjectType.USER,
        externalId(GrantSubjectType.USER),
        RoleFixtures.READER_ROLE,
        CourseScopes.COURSE,
        resourceId,
        null);
  }

  private RoleGrantRequest globalRequest(final GrantSubjectType type, final String subject) {
    return new RoleGrantRequest(
        type, subject, RoleFixtures.READER_ROLE, ResourceScope.GLOBAL, ResourceScope.GLOBAL, null);
  }

  private RoleGrantFilter filter(final RoleGrantRequest request) {
    return new RoleGrantFilter(
        request.subjectType(),
        request.subjectId(),
        request.role(),
        request.scopeType(),
        request.scopeId());
  }

  private String administrator() {
    return jwt(UUID.randomUUID().toString(), RoleFixtures.ADMINISTRATOR_ROLE);
  }

  private static String externalId(final GrantSubjectType type) {
    val subject = UUID.randomUUID().toString();
    return switch (type) {
      case USER -> OidcPrincipalIdEncoder.userId(ISSUER, subject);
      case SERVICE -> OidcPrincipalIdEncoder.serviceId(ISSUER, subject);
    };
  }

  private static Stream<Arguments> invalidSubjects() {
    return Stream.of(
        Arguments.of(GrantSubjectType.USER, "unqualified-subject"),
        Arguments.of(GrantSubjectType.USER, OidcPrincipalIdEncoder.serviceId(ISSUER, "client")),
        Arguments.of(GrantSubjectType.SERVICE, UUID.randomUUID().toString()),
        Arguments.of(GrantSubjectType.SERVICE, OidcPrincipalIdEncoder.userId(ISSUER, "user")));
  }

  private static Stream<Arguments> invalidScopes() {
    return Stream.of(
        Arguments.of(ResourceScope.GLOBAL, UUID.randomUUID().toString()),
        Arguments.of(CourseScopes.COURSE, ResourceScope.GLOBAL),
        Arguments.of("unregistered", UUID.randomUUID().toString()));
  }
}
