package io.trellis.app.platform.application.usecase.audit;

import io.trellis.app.platform.PlatformErrorCode;
import io.trellis.app.platform.application.support.PageSize;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.TrellisException;
import io.trellis.common.page.CursorPage;
import io.trellis.common.security.PlatformPermissions;
import io.trellis.platform.audit.AuditEventFilter;
import io.trellis.platform.audit.AuditEventRecord;
import io.trellis.platform.audit.AuditEventStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Searches the append-only audit trail: the only way to prove after the fact who did what. */
@Service
@RequiredArgsConstructor
public class SearchAuditEventsUseCase {

  private final AuditEventStore auditEvents;

  @RequiresPermission(anyOf = PlatformPermissions.AUDIT_READ)
  @Transactional(readOnly = true)
  public CursorPage<AuditEventRecord> execute(
      RequestContext ctx, AuditEventFilter filter, String after, int first) {
    PageSize.require(first);
    if (filter.occurredFrom() != null
        && filter.occurredTo() != null
        && !filter.occurredFrom().isBefore(filter.occurredTo())) {
      throw new TrellisException(
          PlatformErrorCode.INVALID_TIME_RANGE, "occurredFrom must be before occurredTo");
    }
    return auditEvents.page(filter, after, first);
  }
}
