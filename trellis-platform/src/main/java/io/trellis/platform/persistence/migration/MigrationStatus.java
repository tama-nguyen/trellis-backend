package io.trellis.platform.persistence.migration;

/** Values of {@code migration_step_checkpoint.status}. */
public enum MigrationStatus {
  RUNNING,
  DONE,
  FAILED
}
