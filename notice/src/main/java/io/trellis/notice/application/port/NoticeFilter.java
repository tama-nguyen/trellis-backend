package io.trellis.notice.application.port;

import io.trellis.notice.api.dto.NoticeAudience;
import io.trellis.notice.api.dto.NoticeStatus;
import java.util.Set;

/**
 * What a list may return. Visibility is part of the query, not a filter after it, so pages are full and the
 * cursor never skips rows: {@code (status IN visibleStatuses AND audience IN visibleAudiences) OR author =
 * ownerId}, then narrowed by the client's own {@code audience} and {@code titleContains}.
 *
 * @param unrestricted managers see everything; the visibility sets are ignored
 * @param ownerId the caller's own notices are always visible to them; null for non-user actors
 * @param audience client filter, or null
 * @param titleContains case-insensitive title search, or null
 */
public record NoticeFilter(
        boolean unrestricted,
        Set<NoticeStatus> visibleStatuses,
        Set<NoticeAudience> visibleAudiences,
        String ownerId,
        NoticeAudience audience,
        String titleContains) {
    public NoticeFilter {
        visibleStatuses = Set.copyOf(visibleStatuses);
        visibleAudiences = Set.copyOf(visibleAudiences);
    }
}
