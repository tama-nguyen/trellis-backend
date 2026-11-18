package io.trellis.learningcontent.domain.course.policy;

import io.trellis.learningcontent.api.dto.CourseStatus;
import io.trellis.learningcontent.domain.course.model.Course;
import java.util.EnumSet;
import java.util.Set;

/**
 * Who may see a course: a manager sees every status, everyone else sees ACTIVE ones plus their own. A course
 * the reader may not see must be reported exactly like a missing one (404), so its existence leaks nothing. List
 * queries apply the same rule in SQL ({@code CourseFilter}), never as a filter after the page.
 */
public final class CourseVisibility {
    /** The only status a non-manager sees (besides their own courses). */
    public static final Set<CourseStatus> READER_STATUSES = EnumSet.of(CourseStatus.ACTIVE);

    private CourseVisibility() {}

    public static boolean canSee(CourseReader reader, Course course) {
        if (reader.manager() || (reader.userId() != null && course.isOwnedBy(reader.userId()))) {
            return true;
        }
        return READER_STATUSES.contains(course.status());
    }
}
