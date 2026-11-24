package io.trellis.platform.audit;

import io.trellis.common.page.CursorPage;

/** Read side of the append-only audit store. */
public interface AuditEventStore {
    CursorPage<AuditEventRecord> page(AuditEventFilter filter, String after, int limit);
}
