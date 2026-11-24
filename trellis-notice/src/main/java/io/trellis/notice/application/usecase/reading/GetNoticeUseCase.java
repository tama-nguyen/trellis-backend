package io.trellis.notice.application.usecase.reading;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.SecurityAction;
import io.trellis.notice.api.security.NoticePermissions;
import io.trellis.notice.application.support.NoticeLoader;
import io.trellis.notice.application.view.NoticeView;
import io.trellis.notice.application.view.NoticeViews;
import io.trellis.notice.domain.model.Notice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads one notice. A notice the caller may not see answers 404, never 403. */
@Service
@RequiredArgsConstructor
public class GetNoticeUseCase {

  private final NoticeLoader noticeLoader;

  @SecurityAudited(value = SecurityAction.NOTICE_ACCESS, recordSuccess = false)
  @RequiresPermission(anyOf = {NoticePermissions.READ, NoticePermissions.MANAGE})
  @Transactional(readOnly = true)
  @Observed(name = "notice.get")
  public NoticeView execute(final RequestContext ctx, final String code) {
    final Notice visibleNotice = noticeLoader.loadVisible(ctx, code);
    return NoticeViews.view(visibleNotice);
  }
}
