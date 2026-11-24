package io.trellis.notice.application.view;

import io.trellis.notice.api.dto.NoticeAudience;
import io.trellis.notice.api.dto.NoticeSeverity;
import io.trellis.notice.api.dto.NoticeStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * A notice as clients see it (REST detail, GraphQL {@code Notice}). {@code version} is also the
 * ETag. {@code @NotNull} marks the fields that are always present: the OpenAPI document (and the
 * clients generated from it) declares them required; the others may be null.
 *
 * @param code public identifier, {@code NT-XXXX-XXXX-XXXX}
 * @param publishedAt last publication time; null while a draft
 * @param archivedAt null unless archived
 */
public record NoticeView(
    @NotNull String code,
    @NotNull String title,
    @NotNull String body,
    @NotNull NoticeSeverity severity,
    @NotNull NoticeAudience audience,
    @NotNull NoticeStatus status,
    @NotNull long version,
    @NotNull Instant createdAt,
    @NotNull Instant updatedAt,
    Instant publishedAt,
    Instant archivedAt) {}
