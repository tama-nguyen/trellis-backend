package io.trellis.notice.application.usecase.publishing;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.Idempotent;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.context.RequestContext;
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
 * Publishes and emits {@code notice.published} in the same transaction. With an {@code Idempotency-Key} a
 * retry returns the stored first result instead of failing with "already published".
 *
 * <p>This is the reference stack of the whole base: authorise, replay a stored result for a repeated key,
 * transaction, audit row and outbox row inside it, metrics innermost.
 */
@Service
@RequiredArgsConstructor
public class PublishNoticeUseCase {
    static final String AUDIT_ACTION = "notice.published";

    private final NoticeRepository notices;
    private final NoticeLoader loader;
    private final OutboxWriter outbox;
    private final Clock clock;

    @RequiresPermission(anyOf = NoticePermissions.MANAGE)
    @Idempotent(key = "#idempotencyKey == null ? null : #code + '/' + #idempotencyKey")
    @Transactional
    @Audited(action = AUDIT_ACTION, aggregateType = Notice.AGGREGATE_TYPE, aggregateId = "#code")
    @Observed(name = "notice.publish")
    public NoticeView execute(RequestContext ctx, String code, String idempotencyKey) {
        Notice next = loader.loadVisible(ctx, code).publish(ctx.actor().id(), clock.instant());
        notices.update(next);
        NoticePublishedV1 payload = new NoticePublishedV1(
                next.code().value(),
                next.content().title(),
                next.content().audience(),
                next.publishedAt(),
                next.publishedBy());
        outbox.append(
                ctx,
                OutboxMessage.of(
                                NoticePublishedV1.EVENT_TYPE,
                                Notice.AGGREGATE_TYPE,
                                next.code().value(),
                                payload)
                        .withVersion(NoticePublishedV1.EVENT_VERSION));
        return NoticeViews.view(next);
    }
}
