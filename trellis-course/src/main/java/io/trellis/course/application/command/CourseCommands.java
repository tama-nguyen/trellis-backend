package io.trellis.course.application.command;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.RateLimited;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.TrellisException;
import io.trellis.common.id.ReferenceCodeGenerator;
import io.trellis.common.id.UuidV7;
import io.trellis.common.validation.CommandValidator;
import io.trellis.common.validation.ValidationPipeline;
import io.trellis.course.api.error.CourseErrorCode;
import io.trellis.course.api.error.CourseErrors;
import io.trellis.course.api.security.CoursePermissions;
import io.trellis.course.application.port.CourseRepository;
import io.trellis.course.application.query.CourseAccess;
import io.trellis.course.application.view.CourseView;
import io.trellis.course.application.view.CourseViews;
import io.trellis.course.domain.model.Course;
import io.trellis.course.domain.model.CourseDetails;
import io.trellis.course.domain.value.CourseCode;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write use cases of the Course aggregate (decision T16: {@code <Aggregate>Commands} for writes, {@code
 * <Aggregate>Queries} for reads; a large or distinct use case gets its own class named after it, e.g. {@code
 * ArchiveCourse}; never one {@code CourseService} for everything). This is the only place for
 * {@code @Transactional} and the AOP annotations: authorise, transaction, audit row in that transaction, metrics
 * innermost (AGENTS.md, "AOP annotations").
 *
 * <p>Generated skeleton: an event is not published yet. When the real use case needs one, write
 * {@code contracts/events/course.<something>/v1.schema.json} first, add the payload record in {@code api.event},
 * inject {@code OutboxWriter} and call {@code outbox.append(ctx, OutboxMessage.of(...))} inside the same
 * transaction - {@code trellis-notice} shows all of it.
 */
@Service
public class CourseCommands {
    static final String AUDIT_CREATED = "course.created";
    static final String AUDIT_UPDATED = "course.updated";
    static final String AUDIT_ACTIVATED = "course.activated";
    static final String AUDIT_ARCHIVED = "course.archived";

    private final CourseRepository courses;
    private final ValidationPipeline<CourseDetails> validation;
    private final CourseAccess access;
    private final Clock clock;
    private final ReferenceCodeGenerator codes = new ReferenceCodeGenerator(CourseCode.PREFIX);

    public CourseCommands(
            CourseRepository courses,
            List<CommandValidator<CourseDetails>> validators,
            CourseAccess access,
            Clock clock) {
        this.courses = courses;
        this.validation = new ValidationPipeline<>(validators);
        this.access = access;
        this.clock = clock;
    }

    @RateLimited(permits = 30, per = "PT1M")
    @RequiresPermission(anyOf = CoursePermissions.MANAGE)
    @Transactional
    @Audited(action = AUDIT_CREATED, aggregateType = Course.AGGREGATE_TYPE, aggregateId = "#result.code()")
    @Observed(name = "course.create")
    public CourseView create(RequestContext ctx, CourseDetails details) {
        validation.validate(ctx, details);
        // 60 random bits: a code collision is a once-in-a-billion event. The UNIQUE constraint still catches it
        // (500, the client retries); retrying here would need a savepoint, since Postgres aborts the transaction.
        Course draft = Course.draft(
                UuidV7.next(clock),
                CourseCode.generate(codes),
                details,
                ctx.actor().id(),
                clock.instant());
        courses.insert(draft);
        return CourseViews.view(draft);
    }

    /**
     * @param expectedVersion the version the client edited (from {@code If-Match}); a different current version is
     *     412, a concurrent writer between our read and write is 409
     */
    @RequiresPermission(anyOf = CoursePermissions.MANAGE)
    @Transactional
    @Audited(action = AUDIT_UPDATED, aggregateType = Course.AGGREGATE_TYPE, aggregateId = "#code")
    @Observed(name = "course.update")
    public CourseView update(RequestContext ctx, String code, long expectedVersion, CourseDetails details) {
        Course current = load(ctx, code);
        if (current.version() != expectedVersion) {
            throw new TrellisException(
                    CourseErrorCode.COURSE_VERSION_MISMATCH,
                    "Course " + code + " is at version " + current.version() + ", not " + expectedVersion);
        }
        validation.validate(ctx, details);
        Course next = current.edit(details, clock.instant());
        courses.update(next);
        return CourseViews.view(next);
    }

    @RequiresPermission(anyOf = CoursePermissions.MANAGE)
    @Transactional
    @Audited(action = AUDIT_ACTIVATED, aggregateType = Course.AGGREGATE_TYPE, aggregateId = "#code")
    @Observed(name = "course.activate")
    public CourseView activate(RequestContext ctx, String code) {
        Course next = load(ctx, code).activate(clock.instant());
        courses.update(next);
        return CourseViews.view(next);
    }

    /** Archive, not delete: the row stays for the audit trail and for anything that still references the code. */
    @RequiresPermission(anyOf = CoursePermissions.MANAGE)
    @Transactional
    @Audited(action = AUDIT_ARCHIVED, aggregateType = Course.AGGREGATE_TYPE, aggregateId = "#code")
    @Observed(name = "course.archive")
    public CourseView archive(RequestContext ctx, String code) {
        Course next = load(ctx, code).archive(clock.instant());
        courses.update(next);
        return CourseViews.view(next);
    }

    private Course load(RequestContext ctx, String code) {
        CourseCode parsed = CourseCode.parseOrNotFound(code);
        return access.requireVisible(
                ctx, courses.findByCode(parsed).orElseThrow(() -> CourseErrors.notFound(parsed.value())));
    }
}
