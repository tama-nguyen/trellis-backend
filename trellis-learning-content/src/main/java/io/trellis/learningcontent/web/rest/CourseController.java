package io.trellis.learningcontent.web.rest;

import io.trellis.common.context.RequestContext;
import io.trellis.learningcontent.api.dto.CourseStatus;
import io.trellis.learningcontent.api.error.CourseErrorCode;
import io.trellis.learningcontent.application.course.usecase.authoring.CreateCourseUseCase;
import io.trellis.learningcontent.application.course.usecase.authoring.UpdateCourseUseCase;
import io.trellis.learningcontent.application.course.usecase.publishing.ActivateCourseUseCase;
import io.trellis.learningcontent.application.course.usecase.publishing.ArchiveCourseUseCase;
import io.trellis.learningcontent.application.course.usecase.reading.GetCourseUseCase;
import io.trellis.learningcontent.application.course.usecase.reading.ListCoursesQuery;
import io.trellis.learningcontent.application.course.usecase.reading.ListCoursesUseCase;
import io.trellis.learningcontent.application.course.view.CourseView;
import io.trellis.learningcontent.web.rest.dto.authoring.CourseDetailsRequest;
import io.trellis.learningcontent.web.rest.dto.reading.CoursePage;
import io.trellis.platform.web.rest.VersionETags;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST for commands and integrations (decision T12). Every response that carries a course carries
 * its version as {@code ETag}; {@code PUT} requires it back in {@code If-Match}. Errors are RFC
 * 7807 problems from the platform: this class throws nothing and builds no error body.
 */
@RestController
@RequestMapping(CourseController.BASE)
class CourseController {
  static final String BASE = "/api/courses";
  static final String BY_CODE = "/{code}";

  private final CreateCourseUseCase createCourse;
  private final UpdateCourseUseCase updateCourse;
  private final ActivateCourseUseCase activateCourse;
  private final ArchiveCourseUseCase archiveCourse;
  private final GetCourseUseCase getCourse;
  private final ListCoursesUseCase listCourses;

  CourseController(
      CreateCourseUseCase createCourse,
      UpdateCourseUseCase updateCourse,
      ActivateCourseUseCase activateCourse,
      ArchiveCourseUseCase archiveCourse,
      GetCourseUseCase getCourse,
      ListCoursesUseCase listCourses) {
    this.createCourse = createCourse;
    this.updateCourse = updateCourse;
    this.activateCourse = activateCourse;
    this.archiveCourse = archiveCourse;
    this.getCourse = getCourse;
    this.listCourses = listCourses;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ResponseEntity<CourseView> createCourse(
      RequestContext ctx, @Valid @RequestBody CourseDetailsRequest request) {
    CourseView created = createCourse.execute(ctx, request.toDetails());
    return ResponseEntity.created(URI.create(BASE + "/" + created.code()))
        .eTag(VersionETags.of(created.version()))
        .body(created);
  }

  @GetMapping
  CoursePage listCourses(
      RequestContext ctx,
      @RequestParam(defaultValue = "" + ListCoursesQuery.DEFAULT_FIRST) int first,
      @RequestParam(required = false) String after,
      @RequestParam(required = false) CourseStatus status,
      @RequestParam(required = false) String query) {
    return CoursePage.of(
        listCourses.execute(ctx, new ListCoursesQuery(first, after, status, query)));
  }

  @GetMapping(BY_CODE)
  ResponseEntity<CourseView> getCourse(RequestContext ctx, @PathVariable String code) {
    return withETag(getCourse.execute(ctx, code));
  }

  @PutMapping(BY_CODE)
  ResponseEntity<CourseView> updateCourse(
      RequestContext ctx,
      @PathVariable String code,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody CourseDetailsRequest request) {
    return withETag(
        updateCourse.execute(
            ctx,
            code,
            VersionETags.parse(ifMatch, CourseErrorCode.COURSE_INVALID_PRECONDITION),
            request.toDetails()));
  }

  @PostMapping(BY_CODE + "/activate")
  ResponseEntity<CourseView> activateCourse(RequestContext ctx, @PathVariable String code) {
    return withETag(activateCourse.execute(ctx, code));
  }

  @PostMapping(BY_CODE + "/archive")
  ResponseEntity<CourseView> archiveCourse(RequestContext ctx, @PathVariable String code) {
    return withETag(archiveCourse.execute(ctx, code));
  }

  private static ResponseEntity<CourseView> withETag(CourseView view) {
    return ResponseEntity.ok().eTag(VersionETags.of(view.version())).body(view);
  }
}
