package io.trellis.learningcontent.infrastructure.persistence;

import io.trellis.learningcontent.domain.course.model.Course;
import io.trellis.learningcontent.domain.course.model.CourseDetails;
import io.trellis.learningcontent.domain.course.value.CourseCode;
import io.trellis.learningcontent.infrastructure.persistence.entity.CourseEntity;

/** The one place that converts between the immutable aggregate and the mutable row (ADR 0004). */
final class CourseEntities {

  private CourseEntities() {}

  static Course toDomain(CourseEntity e) {
    return new Course(
        e.getId(),
        new CourseCode(e.getCode()),
        new CourseDetails(e.getName(), e.getDescription()),
        e.getStatus(),
        e.getOwnerId(),
        e.getVersion(),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        e.getActivatedAt(),
        e.getArchivedAt());
  }

  static CourseEntity toEntity(Course c) {
    CourseEntity e = new CourseEntity();
    e.setId(c.id());
    e.setCode(c.code().value());
    e.setName(c.details().name());
    e.setDescription(c.details().description());
    e.setStatus(c.status());
    e.setOwnerId(c.ownerId());
    e.setVersion(c.version());
    e.setCreatedAt(c.createdAt());
    e.setUpdatedAt(c.updatedAt());
    e.setActivatedAt(c.activatedAt());
    e.setArchivedAt(c.archivedAt());
    return e;
  }
}
