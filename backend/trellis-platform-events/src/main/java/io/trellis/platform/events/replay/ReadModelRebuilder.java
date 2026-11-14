package io.trellis.platform.events.replay;

import io.trellis.common.context.RequestContext;

/**
 * Rebuilds one read model (projection, search index, cache table) from the source-of-truth tables, the safety
 * net when events were lost beyond outbox retention or a projection bug corrupted data (DataHub's
 * {@code RestoreIndices}). Implement as a bean; operators trigger it by {@link #name()}.
 *
 * <p>Must be idempotent and resumable: read in keyset batches and upsert, or emit {@code *.snapshot} events
 * through the outbox.
 */
public interface ReadModelRebuilder {
    /** Stable kebab-case name, e.g. {@code notice-stats}. */
    String name();

    /** @return rows or events processed */
    long rebuild(RequestContext ctx);
}
