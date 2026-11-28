package io.trellis.learningcontent.infrastructure.persistence;

import io.trellis.learningcontent.domain.course.model.Course;
import io.trellis.learningcontent.domain.course.model.CourseDetails;
import io.trellis.learningcontent.domain.course.value.CourseCode;
import io.trellis.learningcontent.infrastructure.persistence.entity.CourseEntity;

/** The one place that converts between the immutable aggregate and the mutable row (ADR 0004). */
final class CourseEntities {

  private CourseEntities() {}

  static Course toDomain(final CourseEntity entity) {
    return new Course(
        entity.getId(),
        new CourseCode(entity.getCode()),
        new CourseDetails(entity.getName(), entity.getDescription()),
        entity.getStatus(),
        entity.getOwnerId(),
        entity.getVersion(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getActivatedAt(),
        entity.getArchivedAt());
  }

  static CourseEntity toEntity(final Course.Draft draft) {
    final CourseEntity entity = new CourseEntity();
    entity.setCode(draft.code().value());
    entity.setName(draft.details().name());
    entity.setDescription(draft.details().description());
    entity.setStatus(draft.status());
    entity.setOwnerId(draft.ownerId());
    entity.setVersion(draft.version());
    entity.setCreatedAt(draft.createdAt());
    entity.setUpdatedAt(draft.updatedAt());
    return entity;
  }
}
