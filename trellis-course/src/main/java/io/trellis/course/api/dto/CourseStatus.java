package io.trellis.course.api.dto;

/** Lifecycle: DRAFT -> ACTIVE -> ARCHIVED, and ARCHIVED -> ACTIVE again. Replace with the real states. */
public enum CourseStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED
}
