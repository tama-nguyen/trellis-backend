package io.trellis.notice.application.usecase.publishing;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.Idempotent;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.SecurityAction;
import io.trellis.notice.api.event.NoticePublishedV1;
import io.trellis.notice.api.security.NoticePermissions;
import io.trellis.notice.application.port.NoticeRepository;
import io.trellis.notice.application.support.NoticeLoader;
import io.trellis.notice.application.view.NoticeView;
import io.trellis.notice.application.view.NoticeViews;
import io.trellis.notice.domain.model.Notice;
import io.trellis.platform.events.outbox.OutboxMessage;
import io.trellis.platform.events.outbox.OutboxWriter;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes and emits {@code notice.published} in the same transaction. With an {@code
 * Idempotency-Key} a retry returns the stored first result instead of failing with "already
 * published".
 *
 * <p>This is the reference stack of the whole base: authorise, replay a stored result for a
 * repeated key, transaction, audit row and outbox row inside it, metrics innermost.
 */
@Service
@RequiredArgsConstructor
public class PublishNoticeUseCase {
  static final String AUDIT_ACTION = "notice.published";

  private final NoticeRepository noticeRepository;
  private final NoticeLoader noticeLoader;
  private final OutboxWriter outboxWriter;
  private final Clock clock;

  @SecurityAudited(value = SecurityAction.NOTICE_ACCESS, recordSuccess = false)
  @RequiresPermission(anyOf = NoticePermissions.MANAGE)
  @Idempotent(key = "#idempotencyKey == null ? null : #code + '/' + #idempotencyKey")
  @Transactional
  @Audited(action = AUDIT_ACTION, aggregateType = Notice.AGGREGATE_TYPE, aggregateId = "#code")
  @Observed(name = "notice.publish")
  public NoticeView execute(
      final RequestContext ctx, final String code, final String idempotencyKey) {
    final Notice currentNotice = noticeLoader.loadVisible(ctx, code);
    final Notice publishedNotice = currentNotice.publish(ctx.actor().id(), clock.instant());

    noticeRepository.update(publishedNotice);
    final NoticePublishedV1 noticePublishedEvent =
        new NoticePublishedV1(
            publishedNotice.code().value(),
            publishedNotice.content().title(),
            publishedNotice.content().audience(),
            publishedNotice.publishedAt(),
            publishedNotice.publishedBy());
    final OutboxMessage outboxMessage =
        OutboxMessage.of(
                NoticePublishedV1.EVENT_TYPE,
                Notice.AGGREGATE_TYPE,
                publishedNotice.code().value(),
                noticePublishedEvent)
            .withVersion(NoticePublishedV1.EVENT_VERSION);

    outboxWriter.append(ctx, outboxMessage);

    return NoticeViews.view(publishedNotice);
  }
}
