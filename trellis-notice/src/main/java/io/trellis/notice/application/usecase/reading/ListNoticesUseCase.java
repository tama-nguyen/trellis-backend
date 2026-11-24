package io.trellis.notice.application.usecase.reading;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.page.CursorPage;
import io.trellis.common.security.SecurityAction;
import io.trellis.notice.api.security.NoticePermissions;
import io.trellis.notice.application.port.NoticeFilter;
import io.trellis.notice.application.port.NoticeRepository;
import io.trellis.notice.application.support.NoticeAccess;
import io.trellis.notice.application.view.NoticeSummary;
import io.trellis.notice.application.view.NoticeViews;
import io.trellis.notice.domain.model.Notice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One keyset page. Visibility is part of the query, so a page is never short of rows the caller may
 * see.
 */
@Service
@RequiredArgsConstructor
public class ListNoticesUseCase {

  private final NoticeRepository noticeRepository;
  private final NoticeAccess noticeAccess;

  @SecurityAudited(value = SecurityAction.NOTICE_ACCESS, recordSuccess = false)
  @RequiresPermission(anyOf = {NoticePermissions.READ, NoticePermissions.MANAGE})
  @Transactional(readOnly = true)
  @Observed(name = "notice.list")
  public CursorPage<NoticeSummary> execute(final RequestContext ctx, final ListNoticesQuery query) {
    final NoticeFilter noticeFilter = noticeAccess.listFilter(ctx, query);
    final CursorPage<Notice> noticePage =
        noticeRepository.page(noticeFilter, query.after(), query.first());

    return noticePage.map(NoticeViews::summary);
  }
}
