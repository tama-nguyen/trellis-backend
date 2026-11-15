package io.trellis.course.application.port;

import io.trellis.common.page.CursorPage;
import io.trellis.course.domain.model.Course;
import io.trellis.course.domain.value.CourseCode;
import java.util.Optional;

/** Port to the {@code course} table. Implementations run inside the caller's transaction and never open one. */
public interface CourseRepository {
    Optional<Course> findByCode(CourseCode code);

    void insert(Course course);

    /**
     * Writes {@code next} if the stored row still has {@code next.version() - 1}.
     *
     * @throws io.trellis.common.error.ConcurrentModificationException when another writer won the race
     */
    void update(Course next);

    /** One keyset page, newest first. {@code after} is the opaque cursor of the previous page, or null. */
    CursorPage<Course> page(CourseFilter filter, String after, int limit);
}
