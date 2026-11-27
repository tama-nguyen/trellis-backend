package io.trellis.platform.persistence.migration;

import io.trellis.common.context.RequestContext;
import java.util.Optional;

/**
 * One resumable data migration. Implement as a Spring bean.
 *
 * <p>The runner calls {@link #runBatch} repeatedly, each call inside its own transaction together
 * with the checkpoint update, so a crash loses at most one uncommitted batch and a restart
 * continues where it stopped. Batches must be idempotent anyway: side effects outside the database
 * are not covered by that transaction.
 */
public interface MigrationStep {

  /**
   * Stable unique id, never reused, e.g. {@code 2026-11-notification-backfill-slug}. Stored in the
   * checkpoint table.
   */
  String id();

  /**
   * Blocking steps must be DONE before the serving app reports ready (the app refuses traffic
   * otherwise); non-blocking steps can finish while the app serves.
   */
  boolean blocking();

  /** Lower runs first within the blocking and the non-blocking group. */
  default int order() {
    return 0;
  }

  /**
   * Processes one batch starting after {@code checkpoint} (empty on the first call).
   *
   * @return {@link StepOutcome.Continue} with the new checkpoint, or {@link StepOutcome.Done}
   */
  StepOutcome runBatch(RequestContext ctx, Optional<String> checkpoint);
}
