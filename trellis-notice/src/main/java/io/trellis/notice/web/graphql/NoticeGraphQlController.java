package io.trellis.notice.web.graphql;

import io.trellis.common.context.RequestContext;
import io.trellis.common.page.CursorPage;
import io.trellis.notice.api.dto.NoticeAudience;
import io.trellis.notice.application.usecase.reading.GetNoticeStatsUseCase;
import io.trellis.notice.application.usecase.reading.GetNoticeUseCase;
import io.trellis.notice.application.usecase.reading.ListNoticesQuery;
import io.trellis.notice.application.usecase.reading.ListNoticesUseCase;
import io.trellis.notice.application.view.NoticeStatsView;
import io.trellis.notice.application.view.NoticeSummary;
import io.trellis.notice.application.view.NoticeView;
import io.trellis.platform.web.graphql.GraphQlContextKeys;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * GraphQL read model for the UI (decision T12): {@code graphql/notice/notice.graphqls}. Thin like the REST
 * controller and calling the same {@link NoticeQueries}, which is where authorization happens. {@code stats} is a
 * {@code @BatchMapping}: one stats query per request, however many notices are selected (no N+1).
 */
@Controller
class NoticeGraphQlController {
    private final GetNoticeUseCase getNotice;
    private final ListNoticesUseCase listNotices;
    private final GetNoticeStatsUseCase getNoticeStats;

    NoticeGraphQlController(
            GetNoticeUseCase getNotice, ListNoticesUseCase listNotices, GetNoticeStatsUseCase getNoticeStats) {
        this.getNotice = getNotice;
        this.listNotices = listNotices;
        this.getNoticeStats = getNoticeStats;
    }

    @QueryMapping
    NoticeView notice(@ContextValue(GraphQlContextKeys.REQUEST_CONTEXT) RequestContext ctx, @Argument String code) {
        return getNotice.execute(ctx, code);
    }

    @QueryMapping
    CursorPage<NoticeSummary> notices(
            @ContextValue(GraphQlContextKeys.REQUEST_CONTEXT) RequestContext ctx,
            @Argument Integer first,
            @Argument String after,
            @Argument NoticeAudience audience,
            @Argument String query) {
        int size = first == null ? ListNoticesQuery.DEFAULT_FIRST : first;
        return listNotices.execute(ctx, new ListNoticesQuery(size, after, audience, query));
    }

    @BatchMapping(typeName = "Notice", field = "stats")
    Map<NoticeView, NoticeStatsView> noticeStats(
            List<NoticeView> notices, @ContextValue(GraphQlContextKeys.REQUEST_CONTEXT) RequestContext ctx) {
        return byKey(notices, NoticeView::code, ctx);
    }

    @BatchMapping(typeName = "NoticeSummary", field = "stats")
    Map<NoticeSummary, NoticeStatsView> summaryStats(
            List<NoticeSummary> notices, @ContextValue(GraphQlContextKeys.REQUEST_CONTEXT) RequestContext ctx) {
        return byKey(notices, NoticeSummary::code, ctx);
    }

    /** Batch contract: one entry per source, in source order. */
    private <T> Map<T, NoticeStatsView> byKey(List<T> sources, Function<T, String> codeOf, RequestContext ctx) {
        Map<String, NoticeStatsView> byCode =
                getNoticeStats.execute(ctx, sources.stream().map(codeOf).toList());
        Map<T, NoticeStatsView> result = new LinkedHashMap<>();
        sources.forEach(source -> result.put(source, byCode.get(codeOf.apply(source))));
        return result;
    }
}
