package io.trellis.notice.web.rest.dto.authoring;

import io.trellis.notice.api.dto.NoticeAudience;
import io.trellis.notice.api.dto.NoticeSeverity;
import io.trellis.notice.domain.model.NoticeContent;
import jakarta.validation.constraints.NotNull;

/**
 * Body of create and update. Bean Validation checks shape only (present, right type); the business
 * limits are {@code NoticeContentValidator}'s, so GraphQL or a consumer calling the service get the
 * same rules.
 */
public record NoticeContentRequest(
    @NotNull String title,
    @NotNull String body,
    @NotNull NoticeSeverity severity,
    @NotNull NoticeAudience audience) {
  public NoticeContent toContent() {
    return new NoticeContent(title, body, severity, audience);
  }
}
