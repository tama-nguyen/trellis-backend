package io.trellis.learningcontent.application.course.usecase.reading;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.context.RequestContext;
import io.trellis.learningcontent.application.course.support.CourseLoader;
import io.trellis.learningcontent.application.course.view.CourseView;
import io.trellis.learningcontent.application.course.view.CourseViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads one course. A course the caller may not see answers 404, never 403. */
@Service
@RequiredArgsConstructor
public class GetCourseUseCase {

    private final CourseLoader loader;

    @Transactional(readOnly = true)
    @Observed(name = "course.get")
    public CourseView execute(RequestContext ctx, String code) {
        return CourseViews.view(loader.loadVisible(ctx, code));
    }
}
