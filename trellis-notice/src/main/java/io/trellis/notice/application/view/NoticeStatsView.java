package io.trellis.notice.application.view;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Read model fed by {@code notice.published}: how often a notice was published and when. All
 * zero/null for a notice never published (or whose event the projection has not seen yet: it is
 * eventually consistent).
 */
public record NoticeStatsView(
    @NotNull String noticeCode,
    @NotNull int publishCount,
    Instant firstPublishedAt,
    Instant lastPublishedAt) {
  public static NoticeStatsView empty(String noticeCode) {
    return new NoticeStatsView(noticeCode, 0, null, null);
  }
}
