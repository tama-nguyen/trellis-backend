package io.trellis.notice.web.rest;

import io.trellis.common.context.RequestContext;
import io.trellis.common.http.HttpHeaderNames;
import io.trellis.notice.api.dto.NoticeAudience;
import io.trellis.notice.api.error.NoticeErrorCode;
import io.trellis.notice.application.usecase.authoring.CreateNoticeUseCase;
import io.trellis.notice.application.usecase.authoring.UpdateNoticeUseCase;
import io.trellis.notice.application.usecase.publishing.ArchiveNoticeUseCase;
import io.trellis.notice.application.usecase.publishing.PublishNoticeUseCase;
import io.trellis.notice.application.usecase.reading.GetNoticeUseCase;
import io.trellis.notice.application.usecase.reading.ListNoticesQuery;
import io.trellis.notice.application.usecase.reading.ListNoticesUseCase;
import io.trellis.notice.application.view.NoticeView;
import io.trellis.notice.web.rest.dto.authoring.NoticeContentRequest;
import io.trellis.notice.web.rest.dto.reading.NoticePage;
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
 * REST for commands and integrations (decision T12). Every response that carries a notice carries
 * its version as {@code ETag}; {@code PUT} requires it back in {@code If-Match}. Errors are RFC
 * 7807 problems from the platform. One use case per endpoint, and no rule of its own.
 */
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
class NoticeController {

  private final CreateNoticeUseCase createNotice;
  private final UpdateNoticeUseCase updateNotice;
  private final PublishNoticeUseCase publishNotice;
  private final ArchiveNoticeUseCase archiveNotice;
  private final GetNoticeUseCase getNotice;
  private final ListNoticesUseCase listNotices;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ResponseEntity<NoticeView> createNotice(
      RequestContext ctx, @Valid @RequestBody NoticeContentRequest request) {
    NoticeView created = createNotice.execute(ctx, request.toContent());
    return ResponseEntity.created(URI.create("/api/notices/" + created.code()))
        .eTag(VersionETags.of(created.version()))
        .body(created);
  }

  @GetMapping
  NoticePage listNotices(
      RequestContext ctx,
      @RequestParam(defaultValue = "" + ListNoticesQuery.DEFAULT_FIRST) int first,
      @RequestParam(required = false) String after,
      @RequestParam(required = false) NoticeAudience audience,
      @RequestParam(required = false) String query) {
    return NoticePage.of(
        listNotices.execute(ctx, new ListNoticesQuery(first, after, audience, query)));
  }

  @GetMapping("/{code}")
  ResponseEntity<NoticeView> getNotice(RequestContext ctx, @PathVariable String code) {
    return withETag(getNotice.execute(ctx, code));
  }

  @PutMapping("/{code}")
  ResponseEntity<NoticeView> updateNotice(
      RequestContext ctx,
      @PathVariable String code,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody NoticeContentRequest request) {
    return withETag(
        updateNotice.execute(
            ctx,
            code,
            VersionETags.parse(ifMatch, NoticeErrorCode.NOTICE_INVALID_PRECONDITION),
            request.toContent()));
  }

  @PostMapping("/{code}/publish")
  ResponseEntity<NoticeView> publishNotice(
      RequestContext ctx,
      @PathVariable String code,
      @RequestHeader(name = HttpHeaderNames.IDEMPOTENCY_KEY, required = false)
          String idempotencyKey) {
    return withETag(publishNotice.execute(ctx, code, idempotencyKey));
  }

  @PostMapping("/{code}/archive")
  ResponseEntity<NoticeView> archiveNotice(RequestContext ctx, @PathVariable String code) {
    return withETag(archiveNotice.execute(ctx, code));
  }

  private static ResponseEntity<NoticeView> withETag(NoticeView view) {
    return ResponseEntity.ok().eTag(VersionETags.of(view.version())).body(view);
  }
}
