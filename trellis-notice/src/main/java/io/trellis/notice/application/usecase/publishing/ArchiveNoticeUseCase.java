package io.trellis.notice.application.usecase.publishing;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.notice.api.security.NoticePermissions;
import io.trellis.notice.application.port.NoticeRepository;
import io.trellis.notice.application.support.NoticeLoader;
import io.trellis.notice.application.view.NoticeView;
import io.trellis.notice.application.view.NoticeViews;
import io.trellis.notice.domain.model.Notice;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Takes a notice out of circulation without deleting it; the audit trail still needs the row. */
@Service
@RequiredArgsConstructor
public class ArchiveNoticeUseCase {
  static final String AUDIT_ACTION = "notice.archived";

  private final NoticeRepository notices;
  private final NoticeLoader loader;
  private final Clock clock;

  @RequiresPermission(anyOf = NoticePermissions.MANAGE)
  @Transactional
  @Audited(action = AUDIT_ACTION, aggregateType = Notice.AGGREGATE_TYPE, aggregateId = "#code")
  @Observed(name = "notice.archive")
  public NoticeView execute(RequestContext ctx, String code) {
    Notice next = loader.loadVisible(ctx, code).archive(clock.instant());
    notices.update(next);
    return NoticeViews.view(next);
  }
}
