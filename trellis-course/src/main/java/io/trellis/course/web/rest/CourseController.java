package io.trellis.course.web.rest;

import io.trellis.common.context.RequestContext;
import io.trellis.course.api.dto.CourseStatus;
import io.trellis.course.application.command.CourseCommands;
import io.trellis.course.application.query.CourseQueries;
import io.trellis.course.application.query.ListCoursesQuery;
import io.trellis.course.application.view.CourseView;
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
 * REST for commands and integrations (decision T12). Every response that carries a course carries its version
 * as {@code ETag}; {@code PUT} requires it back in {@code If-Match}. Errors are RFC 7807 problems from the
 * platform: this class throws nothing and builds no error body.
 */
@RestController
@RequestMapping(CourseController.BASE)
class CourseController {
    static final String BASE = "/api/courses";
    static final String BY_CODE = "/{code}";

    private final CourseCommands commands;
    private final CourseQueries queries;

    CourseController(CourseCommands commands, CourseQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<CourseView> createCourse(RequestContext ctx, @Valid @RequestBody CourseDetailsRequest request) {
        CourseView created = commands.create(ctx, request.toDetails());
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
        return CoursePage.of(queries.list(ctx, new ListCoursesQuery(first, after, status, query)));
    }

    @GetMapping(BY_CODE)
    ResponseEntity<CourseView> getCourse(RequestContext ctx, @PathVariable String code) {
        return withETag(queries.get(ctx, code));
    }

    @PutMapping(BY_CODE)
    ResponseEntity<CourseView> updateCourse(
            RequestContext ctx,
            @PathVariable String code,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody CourseDetailsRequest request) {
        return withETag(commands.update(ctx, code, VersionETags.parse(ifMatch), request.toDetails()));
    }

    @PostMapping(BY_CODE + "/activate")
    ResponseEntity<CourseView> activateCourse(RequestContext ctx, @PathVariable String code) {
        return withETag(commands.activate(ctx, code));
    }

    @PostMapping(BY_CODE + "/archive")
    ResponseEntity<CourseView> archiveCourse(RequestContext ctx, @PathVariable String code) {
        return withETag(commands.archive(ctx, code));
    }

    private static ResponseEntity<CourseView> withETag(CourseView view) {
        return ResponseEntity.ok().eTag(VersionETags.of(view.version())).body(view);
    }
}
