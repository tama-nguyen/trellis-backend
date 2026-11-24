package io.trellis.notice.application.usecase.authoring;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.TrellisException;
import io.trellis.common.validation.CommandValidator;
import io.trellis.common.validation.ValidationPipeline;
import io.trellis.notice.api.error.NoticeErrorCode;
import io.trellis.notice.api.security.NoticePermissions;
import io.trellis.notice.application.port.NoticeRepository;
import io.trellis.notice.application.support.NoticeLoader;
import io.trellis.notice.application.view.NoticeView;
import io.trellis.notice.application.view.NoticeViews;
import io.trellis.notice.domain.model.Notice;
import io.trellis.notice.domain.model.NoticeContent;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Edits a notice the client has read, refusing if it moved underneath them. */
@Service
public class UpdateNoticeUseCase {
  static final String AUDIT_ACTION = "notice.updated";

  private final NoticeRepository notices;
  private final NoticeLoader loader;
  private final ValidationPipeline<NoticeContent> validation;
  private final Clock clock;

  public UpdateNoticeUseCase(
      NoticeRepository notices,
      NoticeLoader loader,
      List<CommandValidator<NoticeContent>> validators,
      Clock clock) {
    this.notices = notices;
    this.loader = loader;
    this.validation = new ValidationPipeline<>(validators);
    this.clock = clock;
  }

  /**
   * @param expectedVersion the version the client edited (from {@code If-Match}); a different
   *     current version is 412, a concurrent writer between our read and write is 409
   */
  @RequiresPermission(anyOf = NoticePermissions.MANAGE)
  @Transactional
  @Audited(action = AUDIT_ACTION, aggregateType = Notice.AGGREGATE_TYPE, aggregateId = "#code")
  @Observed(name = "notice.update")
  public NoticeView execute(
      RequestContext ctx, String code, long expectedVersion, NoticeContent content) {
    Notice current = loader.loadVisible(ctx, code);
    if (current.version() != expectedVersion) {
      throw new TrellisException(
          NoticeErrorCode.NOTICE_VERSION_MISMATCH,
          "Notice " + code + " is at version " + current.version() + ", not " + expectedVersion);
    }
    validation.validate(ctx, content);
    Notice next = current.edit(content, clock.instant());
    notices.update(next);
    return NoticeViews.view(next);
  }
}
