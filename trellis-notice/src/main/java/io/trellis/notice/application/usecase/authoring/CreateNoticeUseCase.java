package io.trellis.notice.application.usecase.authoring;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.id.ReferenceCodeGenerator;
import io.trellis.common.id.UuidV7;
import io.trellis.common.security.SecurityAction;
import io.trellis.common.validation.ValidationPipeline;
import io.trellis.notice.api.security.NoticePermissions;
import io.trellis.notice.application.port.NoticeRepository;
import io.trellis.notice.application.view.NoticeView;
import io.trellis.notice.application.view.NoticeViews;
import io.trellis.notice.domain.model.Notice;
import io.trellis.notice.domain.model.NoticeContent;
import io.trellis.notice.domain.value.NoticeCode;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a draft. */
@Service
@RequiredArgsConstructor
public class CreateNoticeUseCase {
  static final String AUDIT_ACTION = "notice.created";

  private final NoticeRepository noticeRepository;
  private final ValidationPipeline<NoticeContent> validationPipeline;
  private final Clock clock;
  private final ReferenceCodeGenerator noticeCodeGenerator =
      new ReferenceCodeGenerator(NoticeCode.PREFIX);

  @SecurityAudited(value = SecurityAction.NOTICE_ACCESS, recordSuccess = false)
  @RequiresPermission(anyOf = NoticePermissions.MANAGE)
  @Transactional
  @Audited(
      action = AUDIT_ACTION,
      aggregateType = Notice.AGGREGATE_TYPE,
      aggregateId = "#result.code()")
  @Observed(name = "notice.create")
  public NoticeView execute(final RequestContext ctx, final NoticeContent content) {
    validationPipeline.validate(ctx, content);

    final Notice draftNotice =
        Notice.draft(
            UuidV7.next(clock),
            NoticeCode.generate(noticeCodeGenerator),
            content,
            ctx.actor().id(),
            clock.instant());

    noticeRepository.insert(draftNotice);

    return NoticeViews.view(draftNotice);
  }
}
