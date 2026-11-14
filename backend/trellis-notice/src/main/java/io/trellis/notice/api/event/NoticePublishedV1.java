package io.trellis.notice.api.event;

import io.trellis.notice.api.dto.NoticeAudience;
import java.time.Instant;

/**
 * Payload of {@code notice.published} v1. Must match {@code contracts/events/notice.published/v1.schema.json}
 * field for field; change the schema first, and make a breaking change a new version.
 */
public record NoticePublishedV1(
        String noticeCode, String title, NoticeAudience audience, Instant publishedAt, String publishedBy) {
    public static final String EVENT_TYPE = "notice.published";
    public static final int EVENT_VERSION = 1;
}
