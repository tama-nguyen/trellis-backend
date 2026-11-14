package io.trellis.notice.application.view;

import io.trellis.notice.domain.model.Notice;

/** Aggregate to views. Kept in one place so REST and GraphQL can never disagree on a field. */
public final class NoticeViews {
    private NoticeViews() {}

    public static NoticeView view(Notice n) {
        return new NoticeView(
                n.code().value(),
                n.content().title(),
                n.content().body(),
                n.content().severity(),
                n.content().audience(),
                n.status(),
                n.version(),
                n.createdAt(),
                n.updatedAt(),
                n.publishedAt(),
                n.archivedAt());
    }

    public static NoticeSummary summary(Notice n) {
        return new NoticeSummary(
                n.code().value(),
                n.content().title(),
                n.content().severity(),
                n.content().audience(),
                n.status(),
                n.createdAt(),
                n.publishedAt());
    }
}
