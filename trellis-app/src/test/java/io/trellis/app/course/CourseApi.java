package io.trellis.app.course;

import io.trellis.app.support.TestApiClient;
import io.trellis.learningcontent.application.course.view.CourseSummary;
import io.trellis.learningcontent.application.course.view.CourseView;
import io.trellis.learningcontent.domain.course.model.Course;
import io.trellis.learningcontent.domain.course.model.CourseDetails;
import io.trellis.platform.web.rest.VersionETags;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
class CourseApi {
  private static final String COURSES = "/api/courses";
  private static final String GRAPHQL = "/api/graphql";
  private static final String ACTIVATE = "/activate";
  private static final int PAGE_SIZE = 50;
  private static final String READ_QUERY =
      """
      query ScopedCourses($code: ID!, $first: Int!) {
        course(code: $code) { code }
        courses(first: $first) { items { code } hasNext }
      }
      """;

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

  HttpResponse<String> graphQl(final int port, final String bearer, final Course course)
      throws Exception {
    val request =
        new GraphQlRequest(READ_QUERY, Map.of("code", course.code().value(), "first", PAGE_SIZE));
    return http.request(port, HttpMethod.POST.name(), GRAPHQL, bearer, request);
  }

  CourseView course(final HttpResponse<String> response) {
    return json.readValue(response.body(), CourseView.class);
  }

  CourseList courses(final HttpResponse<String> response) {
    return json.readValue(response.body(), CourseList.class);
  }

  GraphQlResponse graphQl(final HttpResponse<String> response) {
    return json.readValue(response.body(), GraphQlResponse.class);
  }

  private String path(final Course course) {
    return COURSES + "/" + course.code().value();
  }

  record CourseList(List<CourseSummary> items, String nextCursor, boolean hasNext) {}

  record GraphQlRequest(String query, Map<String, Object> variables) {}

  record GraphQlResponse(GraphQlData data, List<JsonNode> errors) {}

  record GraphQlData(CourseCode course, GraphQlCourses courses) {}

  record CourseCode(String code) {}

  record GraphQlCourses(List<CourseCode> items, boolean hasNext) {}
}
