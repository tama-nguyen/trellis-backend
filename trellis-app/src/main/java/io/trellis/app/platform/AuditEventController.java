package io.trellis.app.platform;

import io.trellis.app.platform.application.support.PageSize;
import io.trellis.app.platform.application.usecase.audit.SearchAuditEventsUseCase;
import io.trellis.app.platform.dto.audit.AuditEventPage;
import io.trellis.common.context.RequestContext;
import io.trellis.platform.audit.AuditEventFilter;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Operator search over the audit trail. */
@RestController
@RequestMapping("/api/platform/audit-events")
@RequiredArgsConstructor
class AuditEventController {
    private final SearchAuditEventsUseCase searchAuditEvents;

    @GetMapping
    AuditEventPage search(
            RequestContext ctx,
            @RequestParam(defaultValue = PageSize.DEFAULT_TEXT) int first,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) String aggregateId,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo) {
        return AuditEventPage.of(searchAuditEvents.execute(
                ctx,
                new AuditEventFilter(aggregateType, aggregateId, actorId, occurredFrom, occurredTo),
                after,
                first));
    }
}
