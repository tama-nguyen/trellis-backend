package io.trellis.notice.application.query;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.annotation.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.common.page.CursorPage;
import io.trellis.notice.api.error.NoticeErrors;
import io.trellis.notice.api.security.NoticePermissions;
import io.trellis.notice.application.port.NoticeRepository;
import io.trellis.notice.application.port.NoticeStatsRepository;
import io.trellis.notice.application.view.NoticeStatsView;
import io.trellis.notice.application.view.NoticeSummary;
import io.trellis.notice.application.view.NoticeView;
import io.trellis.notice.application.view.NoticeViews;
import io.trellis.notice.domain.value.NoticeCode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read use cases of the Notice aggregate (decision T16), shared by REST and GraphQL: read-only transactions that
 * return views. The guard is here (operation permission plus {@link NoticeAccess}), never in a resolver or
 * controller, so both APIs enforce the same rules.
 */
@Service
public class NoticeQueries {
    private final NoticeRepository notices;
    private final NoticeStatsRepository stats;
    private final NoticeAccess access;

    public NoticeQueries(NoticeRepository notices, NoticeStatsRepository stats, NoticeAccess access) {
        this.notices = notices;
        this.stats = stats;
        this.access = access;
    }

    @RequiresPermission(anyOf = {NoticePermissions.READ, NoticePermissions.MANAGE})
    @Transactional(readOnly = true)
    @Observed(name = "notice.get")
    public NoticeView get(RequestContext ctx, String code) {
        NoticeCode parsed = NoticeCode.parseOrNotFound(code);
        return notices.findByCode(parsed)
                .map(n -> access.requireVisible(ctx, n))
                .map(NoticeViews::view)
                .orElseThrow(() -> NoticeErrors.notFound(parsed.value()));
    }

    @RequiresPermission(anyOf = {NoticePermissions.READ, NoticePermissions.MANAGE})
    @Transactional(readOnly = true)
    @Observed(name = "notice.list")
    public CursorPage<NoticeSummary> list(RequestContext ctx, ListNoticesQuery query) {
        return notices.page(access.listFilter(ctx, query), query.after(), query.first())
                .map(NoticeViews::summary);
    }

    /**
     * Stats of notices the caller already holds (they were returned by {@link #get} or {@link #list}, so
     * visibility was checked there). One query for the whole batch; the GraphQL {@code @BatchMapping} calls it once
     * per request, whatever the number of notices.
     */
    @RequiresPermission(anyOf = {NoticePermissions.READ, NoticePermissions.MANAGE})
    @Transactional(readOnly = true)
    @Observed(name = "notice.stats")
    public Map<String, NoticeStatsView> stats(RequestContext ctx, Collection<String> codes) {
        Map<String, NoticeStatsView> found = stats.findByCodes(codes);
        Map<String, NoticeStatsView> result = new LinkedHashMap<>();
        for (String code : codes) {
            result.put(code, found.getOrDefault(code, NoticeStatsView.empty(code)));
        }
        return result;
    }
}
