package io.trellis.app.platform.infrastructure.authorization;

import io.trellis.app.platform.application.port.GrantResourceLookup;
import io.trellis.common.id.EntityIds;
import io.trellis.learningcontent.api.security.CourseScopes;
import io.trellis.learningcontent.application.course.port.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The course authorization scope uses the aggregate Long, not its public reference code. */
@Component
@RequiredArgsConstructor
public class CourseGrantResourceLookup implements GrantResourceLookup {
  private final CourseRepository courseRepository;

  @Override
  public String scopeType() {
    return CourseScopes.COURSE;
  }

  @Override
  public boolean exists(final String resourceId) {
    final Long courseId;
    try {
      courseId = EntityIds.parse(resourceId);
    } catch (IllegalArgumentException malformed) {
      return false;
    }
    return courseRepository.existsById(courseId);
  }
}
