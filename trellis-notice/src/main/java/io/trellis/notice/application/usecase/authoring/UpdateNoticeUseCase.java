package io.trellis.notice.application.usecase.authoring;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.TrellisException;
import io.trellis.common.security.SecurityAction;
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

  private final NoticeRepository noticeRepository;
  private final NoticeLoader noticeLoader;
  private final ValidationPipeline<NoticeContent> validationPipeline;
  private final Clock clock;

  public UpdateNoticeUseCase(
      final NoticeRepository noticeRepository,
      final NoticeLoader noticeLoader,
      final List<CommandValidator<NoticeContent>> validators,
      final Clock clock) {
    this.noticeRepository = noticeRepository;
    this.noticeLoader = noticeLoader;
    this.validationPipeline = new ValidationPipeline<>(validators);
    this.clock = clock;
  }

  /**
   * @param expectedVersion the version the client edited (from {@code If-Match}); a different
   *     current version is 412, a concurrent writer between our read and write is 409
   */
  @SecurityAudited(value = SecurityAction.NOTICE_ACCESS, recordSuccess = false)
  @RequiresPermission(anyOf = NoticePermissions.MANAGE)
  @Transactional
  @Audited(action = AUDIT_ACTION, aggregateType = Notice.AGGREGATE_TYPE, aggregateId = "#code")
  @Observed(name = "notice.update")
  public NoticeView execute(
      final RequestContext ctx,
      final String code,
      final long expectedVersion,
      final NoticeContent content) {
    final Notice currentNotice = noticeLoader.loadVisible(ctx, code);
    if (currentNotice.version() != expectedVersion) {
      throw new TrellisException(
          NoticeErrorCode.NOTICE_VERSION_MISMATCH,
          "Notice "
              + code
              + " is at version "
              + currentNotice.version()
              + ", not "
              + expectedVersion);
    }

    validationPipeline.validate(ctx, content);

    final Notice updatedNotice = currentNotice.edit(content, clock.instant());
    noticeRepository.update(updatedNotice);

    return NoticeViews.view(updatedNotice);
  }
}
