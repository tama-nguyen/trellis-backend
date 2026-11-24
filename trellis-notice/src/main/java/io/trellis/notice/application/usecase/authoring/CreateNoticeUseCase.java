package io.trellis.notice.application.usecase.authoring;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.RateLimited;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.common.id.ReferenceCodeGenerator;
import io.trellis.common.id.UuidV7;
import io.trellis.common.validation.CommandValidator;
import io.trellis.common.validation.ValidationPipeline;
import io.trellis.notice.api.security.NoticePermissions;
import io.trellis.notice.application.port.NoticeRepository;
import io.trellis.notice.application.view.NoticeView;
import io.trellis.notice.application.view.NoticeViews;
import io.trellis.notice.domain.model.Notice;
import io.trellis.notice.domain.model.NoticeContent;
import io.trellis.notice.domain.value.NoticeCode;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a draft. */
@Service
public class CreateNoticeUseCase {
    static final String AUDIT_ACTION = "notice.created";

    private final NoticeRepository notices;
    private final ValidationPipeline<NoticeContent> validation;
    private final Clock clock;
    private final ReferenceCodeGenerator codes = new ReferenceCodeGenerator(NoticeCode.PREFIX);

    public CreateNoticeUseCase(
            NoticeRepository notices, List<CommandValidator<NoticeContent>> validators, Clock clock) {
        this.notices = notices;
        this.validation = new ValidationPipeline<>(validators);
        this.clock = clock;
    }

    @RateLimited(permits = 30, per = "PT1M")
    @RequiresPermission(anyOf = NoticePermissions.MANAGE)
    @Transactional
    @Audited(action = AUDIT_ACTION, aggregateType = Notice.AGGREGATE_TYPE, aggregateId = "#result.code()")
    @Observed(name = "notice.create")
    public NoticeView execute(RequestContext ctx, NoticeContent content) {
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
}
