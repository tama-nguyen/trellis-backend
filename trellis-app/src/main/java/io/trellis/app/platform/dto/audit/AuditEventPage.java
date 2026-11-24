package io.trellis.app.platform.dto.audit;

import io.trellis.common.page.CursorPage;
import io.trellis.platform.audit.AuditEventRecord;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Keyset page of audit events. */
public record AuditEventPage(
        @NotNull List<AuditEventView> items,
        String nextCursor,
        @NotNull boolean hasNext) {
    public static AuditEventPage of(CursorPage<AuditEventRecord> page) {
        CursorPage<AuditEventView> views = page.map(AuditEventView::of);
        return new AuditEventPage(views.items(), views.nextCursor(), views.hasNext());
    }
}
