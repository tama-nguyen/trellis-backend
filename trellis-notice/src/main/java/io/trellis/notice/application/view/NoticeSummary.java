package io.trellis.notice.application.view;

import io.trellis.notice.api.dto.NoticeAudience;
import io.trellis.notice.api.dto.NoticeSeverity;
import io.trellis.notice.api.dto.NoticeStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * List row: a notice without its body. {@code @NotNull} fields are required in the OpenAPI
 * document.
 */
public record NoticeSummary(
    @NotNull String code,
    @NotNull String title,
    @NotNull NoticeSeverity severity,
    @NotNull NoticeAudience audience,
    @NotNull NoticeStatus status,
    @NotNull Instant createdAt,
    Instant publishedAt) {}
