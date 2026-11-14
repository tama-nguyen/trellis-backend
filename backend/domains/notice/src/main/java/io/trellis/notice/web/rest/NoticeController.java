package io.trellis.notice.web.rest;

import io.trellis.common.constants.TrellisHeaders;
import io.trellis.common.context.RequestContext;
import io.trellis.notice.api.dto.NoticeAudience;
import io.trellis.notice.application.command.NoticeCommands;
import io.trellis.notice.application.query.ListNoticesQuery;
import io.trellis.notice.application.query.NoticeQueries;
import io.trellis.notice.application.view.NoticeView;
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
 * REST for commands and integrations (decision T12). Every response that carries a notice carries its version as
 * {@code ETag}; {@code PUT} requires it back in {@code If-Match}. Errors are RFC 7807 problems from the platform.
 */
@RestController
@RequestMapping(NoticeController.BASE)
class NoticeController {
    static final String BASE = "/api/notices";
    static final String BY_CODE = "/{code}";

    private final NoticeCommands commands;
    private final NoticeQueries queries;

    NoticeController(NoticeCommands commands, NoticeQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<NoticeView> createNotice(RequestContext ctx, @Valid @RequestBody NoticeContentRequest request) {
        NoticeView created = commands.create(ctx, request.toContent());
        return ResponseEntity.created(URI.create(BASE + "/" + created.code()))
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
        return NoticePage.of(queries.list(ctx, new ListNoticesQuery(first, after, audience, query)));
    }

    @GetMapping(BY_CODE)
    ResponseEntity<NoticeView> getNotice(RequestContext ctx, @PathVariable String code) {
        return withETag(queries.get(ctx, code));
    }

    @PutMapping(BY_CODE)
    ResponseEntity<NoticeView> updateNotice(
            RequestContext ctx,
            @PathVariable String code,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody NoticeContentRequest request) {
        return withETag(commands.update(ctx, code, VersionETags.parse(ifMatch), request.toContent()));
    }

    @PostMapping(BY_CODE + "/publish")
    ResponseEntity<NoticeView> publishNotice(
            RequestContext ctx,
            @PathVariable String code,
            @RequestHeader(name = TrellisHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        return withETag(commands.publish(ctx, code, idempotencyKey));
    }

    @PostMapping(BY_CODE + "/archive")
    ResponseEntity<NoticeView> archiveNotice(RequestContext ctx, @PathVariable String code) {
        return withETag(commands.archive(ctx, code));
    }

    private static ResponseEntity<NoticeView> withETag(NoticeView view) {
        return ResponseEntity.ok().eTag(VersionETags.of(view.version())).body(view);
    }
}
