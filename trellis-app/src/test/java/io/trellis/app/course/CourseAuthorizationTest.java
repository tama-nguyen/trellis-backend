package io.trellis.app.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.trellis.app.support.SecurityTestContext;
import io.trellis.app.support.TestApiClient;
import io.trellis.common.page.Cursor;
import io.trellis.common.page.CursorCodec;
import io.trellis.learningcontent.api.dto.CourseStatus;
import io.trellis.learningcontent.application.course.view.CourseSummary;
import io.trellis.learningcontent.infrastructure.persistence.CourseJpaRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

@SecurityTestContext
@Import({CourseFixtures.class, CourseApi.class})
@RequiredArgsConstructor
class CourseAuthorizationTest {
  @LocalServerPort private int port;

  private final CourseFixtures fixtures;

  private final CourseApi api;

  private final CourseJpaRepository courses;
  private final CursorCodec cursorCodec;
  private final TestApiClient http;

  @Test
  void shouldRejectSignedUuidCursorAfterSwitchingToLongIds() throws Exception {
    // Given
    val cursor =
        cursorCodec.encode(
            new Cursor(List.of("2026-11-27T00:00:00Z", "0193a5c0-0000-7000-8000-000000000001")));
    val administrator = fixtures.administrator();

    // When
    val response =
        http.request(
            port,
            "GET",
            "/api/courses?first=1&after=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8),
            administrator,
            null);

    // Then
    assertEquals(HttpStatus.BAD_REQUEST.value(), response.statusCode());
    assertEquals("INVALID_CURSOR", http.assertJsonBody(response, 400).path("code").asString());
  }

  @Test
  void readerCannotCreateCourse() throws Exception {
    // Given
    val reader = fixtures.reader();
    val details = fixtures.details();
    val countBefore = courses.count();

    // When
    val response = api.create(port, reader, details);

    // Then
    assertEquals(HttpStatus.FORBIDDEN.value(), response.statusCode());
    assertEquals(countBefore, courses.count());
  }

  @Test
  void globalManagerCanCreateCourse() throws Exception {
    // Given
    val administrator = fixtures.administrator();
    val details = fixtures.details();

    // When
    val response = api.create(port, administrator, details);

    // Then
    assertEquals(HttpStatus.CREATED.value(), response.statusCode());
    val created = api.course(response);
    assertEquals(details.name(), created.name());
    assertEquals(CourseStatus.DRAFT, created.status());
    assertTrue(courses.findByCode(created.code()).isPresent());
  }

  @Test
  void scopedManagerCanUpdateAssignedCourse() throws Exception {
    // Given
    val course = fixtures.draft();
    val manager = fixtures.nativeManagerOf(course, port);
    val edited = fixtures.editedDetails();

    // When
    val response = api.update(port, manager, course, edited);

    // Then
    assertEquals(HttpStatus.OK.value(), response.statusCode());
    assertEquals(edited.name(), api.course(response).name());
    assertEquals(edited.name(), courses.findById(course.id()).orElseThrow().getName());
  }

  @Test
  void scopedManagerCanActivateAssignedCourse() throws Exception {
    // Given
    val course = fixtures.draft();
    val manager = fixtures.nativeManagerOf(course, port);

    // When
    val response = api.activate(port, manager, course);

    // Then
    assertEquals(HttpStatus.OK.value(), response.statusCode());
    assertEquals(CourseStatus.ACTIVE, api.course(response).status());
    assertEquals(CourseStatus.ACTIVE, courses.findById(course.id()).orElseThrow().getStatus());
  }

  @Test
  void scopedManagerCannotActivateAnotherCourse() throws Exception {
    // Given
    val assigned = fixtures.draft();
    val other = fixtures.draft();
    val manager = fixtures.nativeManagerOf(assigned, port);

    // When
    val response = api.activate(port, manager, other);

    // Then
    assertEquals(HttpStatus.NOT_FOUND.value(), response.statusCode());
    assertEquals(CourseStatus.DRAFT, courses.findById(other.id()).orElseThrow().getStatus());
  }

  @Test
  void scopedReaderCanReadAssignedCourse() throws Exception {
    // Given
    val course = fixtures.active();
    val reader = fixtures.readerOf(course);

    // When
    val response = api.detail(port, reader, course);

    // Then
    assertEquals(HttpStatus.OK.value(), response.statusCode());
    assertEquals(course.code().value(), api.course(response).code());
  }

  @Test
  void scopedReaderCannotReadAnotherCourse() throws Exception {
    // Given
    val assigned = fixtures.active();
    val other = fixtures.active();
    val reader = fixtures.readerOf(assigned);

    // When
    val response = api.detail(port, reader, other);

    // Then
    assertEquals(HttpStatus.NOT_FOUND.value(), response.statusCode());
    assertEquals(
        response.headers().firstValue("X-Correlation-Id").orElseThrow(),
        api.correlationId(response));
  }

  @Test
  void scopedReaderListsOnlyAssignedCourse() throws Exception {
    // Given
    val assigned = fixtures.active();
    fixtures.active();
    val reader = fixtures.readerOf(assigned);

    // When
    val response = api.list(port, reader);

    // Then
    assertEquals(HttpStatus.OK.value(), response.statusCode());
    val page = api.courses(response);
    assertEquals(
        List.of(assigned.code().value()), page.items().stream().map(CourseSummary::code).toList());
    assertFalse(page.hasNext());
  }

  @Test
  void cursorContinuesInRestListOrder() throws Exception {
    // Given
    fixtures.active();
    fixtures.active();
    val manager = fixtures.administrator();
    val expectedCourses = api.courses(api.list(port, manager)).items();

    // When
    val first = api.page(port, manager, null);
    val second = api.page(port, manager, first.nextCursor());

    // Then
    assertEquals(expectedCourses.getFirst().code(), first.items().getFirst().code());
    assertEquals(expectedCourses.get(1).code(), second.items().getFirst().code());
  }

  @Test
  void staleCourseEditPreservesTheFirstRevision() throws Exception {
    // Given
    val course = fixtures.draft();
    val manager = fixtures.administrator();
    val edited = fixtures.editedDetails();
    api.update(port, manager, course, edited);

    // When
    val response = api.update(port, manager, course, fixtures.details());

    // Then
    assertEquals(HttpStatus.PRECONDITION_FAILED.value(), response.statusCode());
    val persistedCourse = api.course(api.detail(port, manager, course));
    assertEquals(edited.name(), persistedCourse.name());
  }
}
