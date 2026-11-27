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
import io.trellis.notification.api.dto.NotificationAudience;
import io.trellis.notification.api.dto.NotificationSeverity;
import io.trellis.notification.application.usecase.authoring.CreateNotificationUseCase;
import io.trellis.notification.application.usecase.authoring.UpdateNotificationUseCase;
import io.trellis.notification.domain.model.NotificationContent;
import io.trellis.notification.infrastructure.persistence.NotificationJpaRepository;
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
  private final CreateNotificationUseCase createNotificationUseCase;
  private final UpdateNotificationUseCase updateNotificationUseCase;
  private final CourseJpaRepository courseRepository;
  private final NotificationJpaRepository notificationRepository;
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
  void shouldCollectNotificationViolationsWithoutCreatingNotification() {
    // Given
    val context = context();
    val content = new NotificationContent("", "", null, null);
    val countBefore = notificationRepository.count();

    // When
    val failure =
        assertThrows(
            ValidationException.class, () -> createNotificationUseCase.execute(context, content));

    // Then
    assertEquals(
        List.of("title", "body", "severity", "audience"),
        failure.violations().stream().map(Violation::field).toList());
    assertEquals(countBefore, notificationRepository.count());
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
  void shouldKeepNotificationUnchangedWhenEditedContentIsInvalid() {
    // Given
    val context = context();
    val original =
        new NotificationContent(
            "Original notification",
            "Original body",
            NotificationSeverity.INFO,
            NotificationAudience.ALL);
    val created = createNotificationUseCase.execute(context, original);
    val content = new NotificationContent("", "", null, null);

    // When
    val failure =
        assertThrows(
            ValidationException.class,
            () ->
                updateNotificationUseCase.execute(
                    context, created.code(), created.version(), content));

    // Then
    assertEquals(
        List.of("title", "body", "severity", "audience"),
        failure.violations().stream().map(Violation::field).toList());
    val stored = notificationRepository.findByCode(created.code()).orElseThrow();
    assertEquals(created.title(), stored.getTitle());
    assertEquals(created.body(), stored.getBody());
    assertEquals(created.version(), stored.getVersion());
  }

  private RequestContext context() {
    return RequestContext.system("content-validation-test", clock);
  }
}
