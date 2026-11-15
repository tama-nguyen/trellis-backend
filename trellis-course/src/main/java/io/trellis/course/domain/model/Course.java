package io.trellis.course.domain.model;

import io.trellis.common.error.ConflictException;
import io.trellis.course.api.dto.CourseStatus;
import io.trellis.course.api.error.CourseErrorCode;
import io.trellis.course.domain.value.CourseCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The Course aggregate. Immutable: each transition returns the next state with {@code version + 1}; the
 * repository writes it only if the stored version is still the one that was read (optimistic concurrency).
 *
 * <p>Generated skeleton: add the fields and transitions of the real aggregate here, and keep every rule that can
 * be decided from the aggregate alone in this class (not in the use case, not in the controller).
 *
 * @param version starts at 1, increases by one per change; exposed as the ETag
 */
public record Course(
        UUID id,
        CourseCode code,
        CourseDetails details,
        CourseStatus status,
        String ownerId,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant activatedAt,
        Instant archivedAt) {
    public static final String AGGREGATE_TYPE = "Course";
    static final long FIRST_VERSION = 1;

    public Course {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(ownerId, "ownerId");
    }

    public static Course draft(UUID id, CourseCode code, CourseDetails details, String ownerId, Instant now) {
        return new Course(id, code, details, CourseStatus.DRAFT, ownerId, FIRST_VERSION, now, now, null, null);
    }

    /** Drafts and active courses can be edited; an archived one is frozen until it is activated again. */
    public Course edit(CourseDetails newDetails, Instant now) {
        require(status != CourseStatus.ARCHIVED, "An archived course cannot be edited");
        return new Course(id, code, newDetails, status, ownerId, version + 1, createdAt, now, activatedAt, archivedAt);
    }

    public Course activate(Instant now) {
        require(status != CourseStatus.ACTIVE, "The course is already active");
        return new Course(
                id,
                code,
                details,
                CourseStatus.ACTIVE,
                ownerId,
                version + 1,
                createdAt,
                now,
                activatedAt == null ? now : activatedAt,
                null);
    }

    /** The platform archives instead of deleting: the row stays auditable and a cursor page never shifts. */
    public Course archive(Instant now) {
        require(status != CourseStatus.ARCHIVED, "The course is already archived");
        return new Course(
                id, code, details, CourseStatus.ARCHIVED, ownerId, version + 1, createdAt, now, activatedAt, now);
    }

    public boolean isOwnedBy(String actorId) {
        return ownerId.equals(actorId);
    }

    private static void require(boolean allowed, String message) {
        if (!allowed) {
            throw new ConflictException(CourseErrorCode.COURSE_INVALID_TRANSITION, message);
        }
    }
}
