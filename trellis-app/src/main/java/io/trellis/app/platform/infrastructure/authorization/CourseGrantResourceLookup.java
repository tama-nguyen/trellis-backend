package io.trellis.app.platform.infrastructure.authorization;

import io.trellis.app.platform.application.port.GrantResourceLookup;
import io.trellis.learningcontent.api.security.CourseScopes;
import io.trellis.learningcontent.infrastructure.persistence.CourseJpaRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The course authorization scope uses the aggregate UUID, not its public reference code. */
@Component
@RequiredArgsConstructor
public class CourseGrantResourceLookup implements GrantResourceLookup {
  private final CourseJpaRepository courseRepository;

  @Override
  public String scopeType() {
    return CourseScopes.COURSE;
  }

  @Override
  public boolean exists(final String resourceId) {
    final UUID courseId;
    try {
      courseId = UUID.fromString(resourceId);
    } catch (IllegalArgumentException malformed) {
      return false;
    }
    return courseId.toString().equals(resourceId) && courseRepository.existsById(courseId);
  }
}
