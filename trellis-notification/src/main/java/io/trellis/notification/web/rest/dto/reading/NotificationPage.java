package io.trellis.notification.web.rest.dto.reading;

import io.trellis.common.page.CursorPage;
import io.trellis.notification.application.view.NotificationSummary;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * One keyset page of notifications. Same shape as the GraphQL {@code NotificationPage}: pass {@code
 * nextCursor} as {@code after} to get the next page; it is null exactly when {@code hasNext} is
 * false.
 */
public record NotificationPage(
    @NotNull List<NotificationSummary> items, String nextCursor, @NotNull boolean hasNext) {
  public static NotificationPage of(CursorPage<NotificationSummary> page) {
    return new NotificationPage(page.items(), page.nextCursor(), page.hasNext());
  }
}
