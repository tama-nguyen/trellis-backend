package io.trellis.notice.web.rest;

import io.trellis.common.page.CursorPage;
import io.trellis.notice.application.view.NoticeSummary;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * One keyset page of notices. Same shape as the GraphQL {@code NoticePage}: pass {@code nextCursor} as {@code
 * after} to get the next page; it is null exactly when {@code hasNext} is false.
 */
record NoticePage(
        @NotNull List<NoticeSummary> items,
        String nextCursor,
        @NotNull boolean hasNext) {
    static NoticePage of(CursorPage<NoticeSummary> page) {
        return new NoticePage(page.items(), page.nextCursor(), page.hasNext());
    }
}
