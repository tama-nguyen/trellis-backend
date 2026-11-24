package io.trellis.notice.application.usecase.reading;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.notice.api.security.NoticePermissions;
import io.trellis.notice.application.support.NoticeLoader;
import io.trellis.notice.application.view.NoticeView;
import io.trellis.notice.application.view.NoticeViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads one notice. A notice the caller may not see answers 404, never 403. */
@Service
@RequiredArgsConstructor
public class GetNoticeUseCase {

  private final NoticeLoader loader;

  @RequiresPermission(anyOf = {NoticePermissions.READ, NoticePermissions.MANAGE})
  @Transactional(readOnly = true)
  @Observed(name = "notice.get")
  public NoticeView execute(RequestContext ctx, String code) {
    return NoticeViews.view(loader.loadVisible(ctx, code));
  }
}
