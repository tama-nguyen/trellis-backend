package io.trellis.platform.persistence.migration;

/** Result of one {@link MigrationStep#runBatch} call. */
public sealed interface StepOutcome {

    /** Rows handled in this batch, for progress reporting. */
    long processed();

    /** More work remains; resume after {@code checkpoint}. */
    record Continue(String checkpoint, long processed) implements StepOutcome {}

    /** The step is complete. */
    record Done(long processed) implements StepOutcome {}
}
