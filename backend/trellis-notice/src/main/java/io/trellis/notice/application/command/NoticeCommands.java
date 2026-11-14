package io.trellis.notice.application.command;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.annotation.Audited;
import io.trellis.common.annotation.Idempotent;
import io.trellis.common.annotation.RateLimited;
import io.trellis.common.annotation.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.TrellisException;
import io.trellis.common.id.ReferenceCodeGenerator;
import io.trellis.common.id.UuidV7;
import io.trellis.common.validation.CommandValidator;
import io.trellis.common.validation.ValidationPipeline;
import io.trellis.notice.api.error.NoticeErrorCode;
import io.trellis.notice.api.error.NoticeErrors;
import io.trellis.notice.api.event.NoticePublishedV1;
import io.trellis.notice.api.security.NoticePermissions;
import io.trellis.notice.application.port.NoticeRepository;
import io.trellis.notice.application.query.NoticeAccess;
import io.trellis.notice.application.view.NoticeView;
import io.trellis.notice.application.view.NoticeViews;
import io.trellis.notice.domain.model.Notice;
import io.trellis.notice.domain.model.NoticeContent;
import io.trellis.notice.domain.value.NoticeCode;
import io.trellis.platform.events.outbox.OutboxMessage;
import io.trellis.platform.events.outbox.OutboxWriter;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write use cases of the Notice aggregate (decision T16: {@code <Aggregate>Commands} for writes, {@code
 * <Aggregate>Queries} for reads; a large or distinct use case gets its own class named after it, e.g. {@code
 * PublishNotice}; never one {@code NoticeService} for everything). Transactional, emit events, carry the AOP
 * annotations. The stack on {@link #publish} is the reference: authorise, replay a stored result for a repeated
 * {@code Idempotency-Key}, transaction, audit row and outbox row in that transaction, metrics innermost.
 * Controllers (REST, GraphQL) only translate and call these methods.
 */
@Service
public class NoticeCommands {
    static final String AUDIT_CREATED = "notice.created";
    static final String AUDIT_UPDATED = "notice.updated";
    static final String AUDIT_PUBLISHED = "notice.published";
    static final String AUDIT_ARCHIVED = "notice.archived";

    private final NoticeRepository notices;
    private final OutboxWriter outbox;
    private final ValidationPipeline<NoticeContent> validation;
    private final NoticeAccess access;
    private final Clock clock;
    private final ReferenceCodeGenerator codes = new ReferenceCodeGenerator(NoticeCode.PREFIX);

    public NoticeCommands(
            NoticeRepository notices,
            OutboxWriter outbox,
            List<CommandValidator<NoticeContent>> validators,
            NoticeAccess access,
            Clock clock) {
        this.notices = notices;
        this.outbox = outbox;
        this.validation = new ValidationPipeline<>(validators);
        this.access = access;
        this.clock = clock;
    }

    @RateLimited(permits = 30, per = "PT1M")
    @RequiresPermission(anyOf = NoticePermissions.MANAGE)
    @Transactional
    @Audited(action = AUDIT_CREATED, aggregateType = Notice.AGGREGATE_TYPE, aggregateId = "#result.code()")
    @Observed(name = "notice.create")
    public NoticeView create(RequestContext ctx, NoticeContent content) {
        validation.validate(ctx, content);
        // 60 random bits: a code collision is a once-in-a-billion event. The UNIQUE constraint still catches it
        // (500, the client retries); retrying here would need a savepoint, since Postgres aborts the transaction.
        Notice draft = Notice.draft(
                UuidV7.next(clock),
                NoticeCode.generate(codes),
                content,
                ctx.actor().id(),
                clock.instant());
        notices.insert(draft);
        return NoticeViews.view(draft);
    }

    /**
     * @param expectedVersion the version the client edited (from {@code If-Match}); a different current version is
     *     412, a concurrent writer between our read and write is 409
     */
    @RequiresPermission(anyOf = NoticePermissions.MANAGE)
    @Transactional
    @Audited(action = AUDIT_UPDATED, aggregateType = Notice.AGGREGATE_TYPE, aggregateId = "#code")
    @Observed(name = "notice.update")
    public NoticeView update(RequestContext ctx, String code, long expectedVersion, NoticeContent content) {
        Notice current = load(ctx, code);
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

    /**
     * Publishes and emits {@code notice.published} in the same transaction. With an {@code Idempotency-Key} a
     * retry returns the stored first result instead of failing with "already published".
     */
    @RequiresPermission(anyOf = NoticePermissions.MANAGE)
    @Idempotent(key = "#idempotencyKey == null ? null : #code + '/' + #idempotencyKey")
    @Transactional
    @Audited(action = AUDIT_PUBLISHED, aggregateType = Notice.AGGREGATE_TYPE, aggregateId = "#code")
    @Observed(name = "notice.publish")
    public NoticeView publish(RequestContext ctx, String code, String idempotencyKey) {
        Notice next = load(ctx, code).publish(ctx.actor().id(), clock.instant());
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

    @RequiresPermission(anyOf = NoticePermissions.MANAGE)
    @Transactional
    @Audited(action = AUDIT_ARCHIVED, aggregateType = Notice.AGGREGATE_TYPE, aggregateId = "#code")
    @Observed(name = "notice.archive")
    public NoticeView archive(RequestContext ctx, String code) {
        Notice next = load(ctx, code).archive(clock.instant());
        notices.update(next);
        return NoticeViews.view(next);
    }

    private Notice load(RequestContext ctx, String code) {
        NoticeCode parsed = NoticeCode.parseOrNotFound(code);
        return access.requireVisible(
                ctx, notices.findByCode(parsed).orElseThrow(() -> NoticeErrors.notFound(parsed.value())));
    }
}
