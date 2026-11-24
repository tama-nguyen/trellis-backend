package io.trellis.notice.domain.model;

import io.trellis.notice.api.dto.NoticeAudience;
import io.trellis.notice.api.dto.NoticeSeverity;

/**
 * The editable part of a notice; the command of both create and update. Checked by the
 * application's validation pipeline, not here, so every violation is reported at once.
 */
public record NoticeContent(
    String title, String body, NoticeSeverity severity, NoticeAudience audience) {
  /**
   * Limits shared with the database columns and the event contract ({@code title} maxLength 200).
   */
  public static final int TITLE_MAX = 200;

  public static final int BODY_MAX = 20_000;
}
