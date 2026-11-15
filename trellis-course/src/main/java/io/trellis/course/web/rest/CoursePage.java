package io.trellis.course.web.rest;

import io.trellis.common.page.CursorPage;
import io.trellis.course.application.view.CourseSummary;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * One keyset page of courses. Same shape as the GraphQL {@code CoursePage}: pass {@code nextCursor} as {@code
 * after} to get the next page; it is null exactly when {@code hasNext} is false.
 */
record CoursePage(
        @NotNull List<CourseSummary> items,
        String nextCursor,
        @NotNull boolean hasNext) {
    static CoursePage of(CursorPage<CourseSummary> page) {
        return new CoursePage(page.items(), page.nextCursor(), page.hasNext());
    }
}
