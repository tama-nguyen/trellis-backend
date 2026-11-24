package io.trellis.app.course;

import static io.trellis.app.support.TestJwtIssuer.ISSUER;
import static io.trellis.app.support.TestJwtIssuer.jwt;

import io.trellis.app.support.IdentityFixtures;
import io.trellis.app.support.TestApiClient;
import io.trellis.common.id.ReferenceCodeGenerator;
import io.trellis.common.id.UuidV7;
import io.trellis.common.security.ResourceScope;
import io.trellis.identity.domain.user.UserStatus;
import io.trellis.learningcontent.application.course.port.CourseRepository;
import io.trellis.learningcontent.domain.course.model.Course;
import io.trellis.learningcontent.domain.course.model.CourseDetails;
import io.trellis.learningcontent.domain.course.value.CourseCode;
import io.trellis.platform.security.authz.GrantSubjectType;
import io.trellis.platform.security.authz.RoleGrantStore;
import io.trellis.platform.security.jwt.OidcPrincipalIdEncoder;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.val;

@RequiredArgsConstructor
class CourseFixtures {
  private static final String ADMINISTRATOR_ROLE = "platform-admin";
  private static final String READER_ROLE = "learner";
  private static final String RESOURCE_TYPE = "course";
  private static final String GRANTED_BY = "course-test-fixture";

  private final CourseRepository courses;
  private final RoleGrantStore grants;
  private final IdentityFixtures identities;
  private final TestApiClient api;
  private final Clock clock;
  private final ReferenceCodeGenerator codes = new ReferenceCodeGenerator(CourseCode.PREFIX);

  Course draft() {
    return persist(false);
  }

  Course active() {
    return persist(true);
  }

  CourseDetails details() {
    return new CourseDetails("Course fixture", "Course authorization fixture");
  }

  CourseDetails editedDetails() {
    return new CourseDetails("Edited course", "Updated by a scoped manager");
  }

  String administrator() {
    return jwt(UUID.randomUUID().toString(), ADMINISTRATOR_ROLE);
  }

  String reader() {
    return jwt(UUID.randomUUID().toString(), READER_ROLE);
  }

  String readerOf(final Course course) {
    val subject = UUID.randomUUID().toString();
    grant(OidcPrincipalIdEncoder.userId(ISSUER, subject), READER_ROLE, course);
    // No role claim: READ comes exclusively from the persisted course-scoped grant.
    return jwt(subject, null);
  }

  String nativeManagerOf(final Course course, final int port) throws Exception {
    val user = identities.nativeUser(UserStatus.ACTIVE);
    grant(user.toString(), ADMINISTRATOR_ROLE, course);
    return api.login(port, user);
  }

  private Course persist(final boolean active) {
    val now = clock.instant();
    val draft =
        Course.draft(
            UuidV7.next(clock),
            CourseCode.generate(codes),
            details(),
            UUID.randomUUID().toString(),
            now);
    val course = active ? draft.activate(now) : draft;
    identities.tx().executeWithoutResult(status -> courses.insert(course));
    return course;
  }

  private void grant(final String subject, final String role, final Course course) {
    identities
        .tx()
        .executeWithoutResult(
            status ->
                grants.grant(
                    GrantSubjectType.USER,
                    subject,
                    role,
                    ResourceScope.of(RESOURCE_TYPE, course.id().toString()),
                    clock.instant(),
                    GRANTED_BY,
                    null));
  }
}
