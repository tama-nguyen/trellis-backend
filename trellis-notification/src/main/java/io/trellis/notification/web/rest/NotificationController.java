package io.trellis.notification.web.rest;

import io.trellis.common.aop.RateLimited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.http.HttpHeaderNames;
import io.trellis.notification.api.dto.NotificationAudience;
import io.trellis.notification.api.error.NotificationErrorCode;
import io.trellis.notification.api.security.NotificationRateLimitPolicies;
import io.trellis.notification.application.usecase.authoring.CreateNotificationUseCase;
import io.trellis.notification.application.usecase.authoring.UpdateNotificationUseCase;
import io.trellis.notification.application.usecase.publishing.ArchiveNotificationUseCase;
import io.trellis.notification.application.usecase.publishing.PublishNotificationUseCase;
import io.trellis.notification.application.usecase.reading.GetNotificationUseCase;
import io.trellis.notification.application.usecase.reading.ListNotificationsQuery;
import io.trellis.notification.application.usecase.reading.ListNotificationsUseCase;
import io.trellis.notification.application.view.NotificationView;
import io.trellis.notification.web.rest.dto.authoring.NotificationContentRequest;
import io.trellis.notification.web.rest.dto.reading.NotificationPage;
import io.trellis.platform.web.rest.VersionETags;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
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
 * REST for commands and integrations (decision T12). Every response that carries a notification
 * carries its version as {@code ETag}; {@code PUT} requires it back in {@code If-Match}. Errors are
 * RFC 7807 problems from the platform. One use case per endpoint, and no rule of its own.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
class NotificationController {

  private final CreateNotificationUseCase createNotification;
  private final UpdateNotificationUseCase updateNotification;
  private final PublishNotificationUseCase publishNotification;
  private final ArchiveNotificationUseCase archiveNotification;
  private final GetNotificationUseCase getNotification;
  private final ListNotificationsUseCase listNotifications;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RateLimited(policy = NotificationRateLimitPolicies.CREATE)
  public ResponseEntity<NotificationView> createNotification(
      RequestContext ctx, @Valid @RequestBody NotificationContentRequest request) {
    NotificationView created = createNotification.execute(ctx, request.toContent());
    return ResponseEntity.created(URI.create("/api/notifications/" + created.code()))
        .eTag(VersionETags.of(created.version()))
        .body(created);
  }

  @GetMapping
  NotificationPage listNotifications(
      RequestContext ctx,
      @RequestParam(defaultValue = "" + ListNotificationsQuery.DEFAULT_FIRST) int first,
      @RequestParam(required = false) String after,
      @RequestParam(required = false) NotificationAudience audience,
      @RequestParam(required = false) String query) {
    return NotificationPage.of(
        listNotifications.execute(ctx, new ListNotificationsQuery(first, after, audience, query)));
  }

  @GetMapping("/{code}")
  ResponseEntity<NotificationView> getNotification(RequestContext ctx, @PathVariable String code) {
    return withETag(getNotification.execute(ctx, code));
  }

  @PutMapping("/{code}")
  ResponseEntity<NotificationView> updateNotification(
      RequestContext ctx,
      @PathVariable String code,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody NotificationContentRequest request) {
    return withETag(
        updateNotification.execute(
            ctx,
            code,
            VersionETags.parse(ifMatch, NotificationErrorCode.NOTIFICATION_INVALID_PRECONDITION),
            request.toContent()));
  }

  @PostMapping("/{code}/publish")
  ResponseEntity<NotificationView> publishNotification(
      RequestContext ctx,
      @PathVariable String code,
      @RequestHeader(name = HttpHeaderNames.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey) {
    return withETag(publishNotification.execute(ctx, code, idempotencyKey));
  }

  @PostMapping("/{code}/archive")
  ResponseEntity<NotificationView> archiveNotification(
      RequestContext ctx, @PathVariable String code) {
    return withETag(archiveNotification.execute(ctx, code));
  }

  private static ResponseEntity<NotificationView> withETag(NotificationView view) {
    return ResponseEntity.ok().eTag(VersionETags.of(view.version())).body(view);
  }
}
