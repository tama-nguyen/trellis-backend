package io.trellis.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.trellis.app.support.SecurityTestContext;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.ValidationException;
import io.trellis.common.validation.Violation;
import io.trellis.learningcontent.application.course.usecase.authoring.CreateCourseUseCase;
import io.trellis.learningcontent.application.course.usecase.authoring.UpdateCourseUseCase;
import io.trellis.learningcontent.domain.course.model.CourseDetails;
import io.trellis.learningcontent.infrastructure.persistence.CourseJpaRepository;
import io.trellis.notice.api.dto.NoticeAudience;
import io.trellis.notice.api.dto.NoticeSeverity;
import io.trellis.notice.application.usecase.authoring.CreateNoticeUseCase;
import io.trellis.notice.application.usecase.authoring.UpdateNoticeUseCase;
import io.trellis.notice.domain.model.NoticeContent;
import io.trellis.notice.infrastructure.persistence.NoticeJpaRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.junit.jupiter.api.Test;

@SecurityTestContext
@RequiredArgsConstructor
class ContentValidationTest {
  private final CreateCourseUseCase createCourseUseCase;
  private final UpdateCourseUseCase updateCourseUseCase;
  private final CreateNoticeUseCase createNoticeUseCase;
  private final UpdateNoticeUseCase updateNoticeUseCase;
  private final CourseJpaRepository courseRepository;
  private final NoticeJpaRepository noticeRepository;
  private final Clock clock;

  @Test
  void shouldCollectCourseViolationsWithoutCreatingCourse() {
    // Given
    val context = context();
    val details = new CourseDetails("", "");
    val countBefore = courseRepository.count();

    // When
    val failure =
        assertThrows(
            ValidationException.class, () -> createCourseUseCase.execute(context, details));

    // Then
    assertEquals(
        List.of("name", "description"),
        failure.violations().stream().map(Violation::field).toList());
    assertEquals(countBefore, courseRepository.count());
  }

  @Test
  void shouldCollectNoticeViolationsWithoutCreatingNotice() {
    // Given
    val context = context();
    val content = new NoticeContent("", "", null, null);
    val countBefore = noticeRepository.count();

    // When
    val failure =
        assertThrows(
            ValidationException.class, () -> createNoticeUseCase.execute(context, content));

    // Then
    assertEquals(
        List.of("title", "body", "severity", "audience"),
        failure.violations().stream().map(Violation::field).toList());
    assertEquals(countBefore, noticeRepository.count());
  }

  @Test
  void shouldKeepCourseUnchangedWhenEditedDetailsAreInvalid() {
    // Given
    val context = context();
    val created =
        createCourseUseCase.execute(
            context, new CourseDetails("Original course", "Original description"));
    val details = new CourseDetails("", "");

    // When
    val failure =
        assertThrows(
            ValidationException.class,
            () -> updateCourseUseCase.execute(context, created.code(), created.version(), details));

    // Then
    assertEquals(
        List.of("name", "description"),
        failure.violations().stream().map(Violation::field).toList());
    val stored = courseRepository.findByCode(created.code()).orElseThrow();
    assertEquals(created.name(), stored.getName());
    assertEquals(created.description(), stored.getDescription());
    assertEquals(created.version(), stored.getVersion());
  }

  @Test
  void shouldKeepNoticeUnchangedWhenEditedContentIsInvalid() {
    // Given
    val context = context();
    val original =
        new NoticeContent(
            "Original notice", "Original body", NoticeSeverity.INFO, NoticeAudience.ALL);
    val created = createNoticeUseCase.execute(context, original);
    val content = new NoticeContent("", "", null, null);

    // When
    val failure =
        assertThrows(
            ValidationException.class,
            () -> updateNoticeUseCase.execute(context, created.code(), created.version(), content));

    // Then
    assertEquals(
        List.of("title", "body", "severity", "audience"),
        failure.violations().stream().map(Violation::field).toList());
    val stored = noticeRepository.findByCode(created.code()).orElseThrow();
    assertEquals(created.title(), stored.getTitle());
    assertEquals(created.body(), stored.getBody());
    assertEquals(created.version(), stored.getVersion());
  }

  private RequestContext context() {
    return RequestContext.system("content-validation-test", clock);
  }
}
