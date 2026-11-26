package io.trellis.learningcontent.web.rest.dto.reading;

import io.trellis.common.page.CursorPage;
import io.trellis.learningcontent.application.course.view.CourseSummary;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * One keyset page of courses. Pass {@code nextCursor} as {@code after} to get the next page; it is
 * null exactly when {@code hasNext} is false.
 */
public record CoursePage(
    @NotNull List<CourseSummary> items, String nextCursor, @NotNull boolean hasNext) {
  public static CoursePage of(CursorPage<CourseSummary> page) {
    return new CoursePage(page.items(), page.nextCursor(), page.hasNext());
  }
}
