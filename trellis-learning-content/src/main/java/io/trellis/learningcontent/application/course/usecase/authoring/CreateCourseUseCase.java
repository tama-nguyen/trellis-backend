package io.trellis.learningcontent.application.course.usecase.authoring;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.RateLimited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.id.ReferenceCodeGenerator;
import io.trellis.common.id.UuidV7;
import io.trellis.common.validation.CommandValidator;
import io.trellis.common.validation.ValidationPipeline;
import io.trellis.learningcontent.application.course.port.CourseRepository;
import io.trellis.learningcontent.application.course.view.CourseView;
import io.trellis.learningcontent.application.course.view.CourseViews;
import io.trellis.learningcontent.domain.course.model.Course;
import io.trellis.learningcontent.domain.course.model.CourseDetails;
import io.trellis.learningcontent.domain.course.value.CourseCode;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a draft course owned by the caller. */
@Service
public class CreateCourseUseCase {
    static final String AUDIT_ACTION = "course.created";

    private final CourseRepository courses;
    private final ValidationPipeline<CourseDetails> validation;
    private final Clock clock;
    private final ReferenceCodeGenerator codes = new ReferenceCodeGenerator(CourseCode.PREFIX);

    public CreateCourseUseCase(
            CourseRepository courses, List<CommandValidator<CourseDetails>> validators, Clock clock) {
        this.courses = courses;
        this.validation = new ValidationPipeline<>(validators);
        this.clock = clock;
    }

    @RateLimited(permits = 30, per = "PT1M")
    @Transactional
    @Audited(action = AUDIT_ACTION, aggregateType = Course.AGGREGATE_TYPE, aggregateId = "#result.code()")
    @Observed(name = "course.create")
    public CourseView execute(RequestContext ctx, CourseDetails details) {
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
}
