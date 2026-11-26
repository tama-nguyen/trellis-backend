package io.trellis.app.course;

import io.trellis.app.support.TestApiClient;
import io.trellis.learningcontent.application.course.view.CourseSummary;
import io.trellis.learningcontent.application.course.view.CourseView;
import io.trellis.learningcontent.domain.course.model.Course;
import io.trellis.learningcontent.domain.course.model.CourseDetails;
import io.trellis.platform.web.rest.VersionETags;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
class CourseApi {
  private static final String COURSES = "/api/courses";
  private static final String ACTIVATE = "/activate";
  private static final int PAGE_SIZE = 50;
  private final TestApiClient http;
  private final JsonMapper json;

  HttpResponse<String> create(final int port, final String bearer, final CourseDetails details)
      throws Exception {
    return http.request(port, HttpMethod.POST.name(), COURSES, bearer, details);
  }

  HttpResponse<String> detail(final int port, final String bearer, final Course course)
      throws Exception {
    return http.request(port, HttpMethod.GET.name(), path(course), bearer, null);
  }

  HttpResponse<String> list(final int port, final String bearer) throws Exception {
    return http.request(port, HttpMethod.GET.name(), COURSES + "?first=" + PAGE_SIZE, bearer, null);
  }

  HttpResponse<String> update(
      final int port, final String bearer, final Course course, final CourseDetails details)
      throws Exception {
    val headers = Map.of(HttpHeaders.IF_MATCH, VersionETags.of(course.version()));
    return http.request(port, HttpMethod.PUT.name(), path(course), bearer, details, headers);
  }

  HttpResponse<String> activate(final int port, final String bearer, final Course course)
      throws Exception {
    return http.request(port, HttpMethod.POST.name(), path(course) + ACTIVATE, bearer, null);
  }

  CourseView course(final HttpResponse<String> response) {
    return json.readValue(response.body(), CourseView.class);
  }

  CourseList page(final int port, final String bearer, final String cursor) throws Exception {
    val after = cursor == null ? "" : "&after=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
    val response =
        http.request(port, HttpMethod.GET.name(), COURSES + "?first=1" + after, bearer, null);
    return courses(response);
  }

  CourseList courses(final HttpResponse<String> response) {
    return json.readValue(response.body(), CourseList.class);
  }

  String correlationId(final HttpResponse<String> response) {
    return json.readTree(response.body()).path("correlationId").asString();
  }

  private String path(final Course course) {
    return COURSES + "/" + course.code().value();
  }

  record CourseList(List<CourseSummary> items, String nextCursor, boolean hasNext) {}
}
