package io.trellis.platform.persistence.migration;

import io.trellis.common.context.RequestContext;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.zip.CRC32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs {@link MigrationStep}s batch by batch with a checkpoint row per step. Safe to start on several
 * instances: each batch takes a transaction-scoped advisory lock on the step, so only one instance advances a
 * step at a time and the others skip it.
 */
public final class MigrationRunner {
    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);
    private static final String SYSTEM_COMPONENT = "migration-runner";
    // Namespace of advisory-lock ids for migration steps; distinct from the outbox relay's namespace.
    private static final long LOCK_NAMESPACE = 0x7E11_0000_0000L;
    private static final int LAST_ERROR_MAX = 2000;

    private static final String TRY_LOCK = "SELECT pg_try_advisory_xact_lock(:lockId)";
    private static final String SELECT_STATE =
            "SELECT status, checkpoint FROM migration_step_checkpoint WHERE step_id = :stepId";
    private static final String UPSERT_PROGRESS = """
            INSERT INTO migration_step_checkpoint
                   (step_id, status, blocking, checkpoint, processed, attempts, started_at, updated_at, finished_at)
            VALUES (:stepId, :status, :blocking, :checkpoint, :processed, 1, :now, :now, :finishedAt)
            ON CONFLICT (step_id) DO UPDATE
               SET status = :status, checkpoint = :checkpoint,
                   processed = migration_step_checkpoint.processed + :processed,
                   updated_at = :now, finished_at = :finishedAt, last_error = NULL
            """;
    private static final String RECORD_FAILURE = """
            INSERT INTO migration_step_checkpoint
                   (step_id, status, blocking, processed, attempts, last_error, started_at, updated_at)
            VALUES (:stepId, 'FAILED', :blocking, 0, 1, :error, :now, :now)
            ON CONFLICT (step_id) DO UPDATE
               SET status = 'FAILED', attempts = migration_step_checkpoint.attempts + 1,
                   last_error = :error, updated_at = :now
            """;

    private final List<MigrationStep> steps;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public MigrationRunner(List<MigrationStep> steps, JdbcClient jdbc, TransactionTemplate tx, Clock clock) {
        this.steps = steps.stream()
                .sorted(Comparator.comparing((MigrationStep s) -> !s.blocking()).thenComparingInt(MigrationStep::order))
                .toList();
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
    }

    /** Runs the selected steps to completion. Returns false if any step failed. */
    public boolean run(boolean includeBlocking, boolean includeNonBlocking) {
        boolean ok = true;
        for (MigrationStep step : steps) {
            if (step.blocking() ? includeBlocking : includeNonBlocking) {
                ok &= runStep(step);
            }
        }
        return ok;
    }

    /** Blocking steps that are registered but not DONE; the serving app refuses traffic while any exist. */
    public List<String> pendingBlockingSteps() {
        return steps.stream()
                .filter(MigrationStep::blocking)
                .filter(step -> !MigrationStatus.DONE.name().equals(state(step).status()))
                .map(MigrationStep::id)
                .toList();
    }

    private boolean runStep(MigrationStep step) {
        RequestContext ctx = RequestContext.system(SYSTEM_COMPONENT, clock);
        log.atInfo().addKeyValue("step", step.id()).log("Migration step starting");
        try {
            while (true) {
                Boolean more = tx.execute(status -> runOneBatch(ctx, step));
                if (!Boolean.TRUE.equals(more)) {
                    log.atInfo().addKeyValue("step", step.id()).log("Migration step finished or held elsewhere");
                    return true;
                }
            }
        } catch (RuntimeException e) {
            log.atError().addKeyValue("step", step.id()).setCause(e).log("Migration step failed");
            tx.executeWithoutResult(status -> jdbc.sql(RECORD_FAILURE)
                    .param("stepId", step.id())
                    .param("blocking", step.blocking())
                    .param("error", truncate(e.toString()))
                    .param("now", Timestamp.from(clock.instant()))
                    .update());
            return false;
        }
    }

    /** One transaction: lock the step, read the checkpoint, run a batch, save the new checkpoint. */
    private boolean runOneBatch(RequestContext ctx, MigrationStep step) {
        Boolean locked = jdbc.sql(TRY_LOCK)
                .param("lockId", lockId(step.id()))
                .query(Boolean.class)
                .single();
        if (!Boolean.TRUE.equals(locked)) {
            return false;
        }
        StepState state = state(step);
        if (MigrationStatus.DONE.name().equals(state.status())) {
            return false;
        }
        StepOutcome outcome = step.runBatch(ctx, Optional.ofNullable(state.checkpoint()));
        boolean done = outcome instanceof StepOutcome.Done;
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.sql(UPSERT_PROGRESS)
                .param("stepId", step.id())
                .param("status", done ? MigrationStatus.DONE.name() : MigrationStatus.RUNNING.name())
                .param("blocking", step.blocking())
                .param("checkpoint", outcome instanceof StepOutcome.Continue c ? c.checkpoint() : state.checkpoint())
                .param("processed", outcome.processed())
                .param("now", now)
                .param("finishedAt", done ? now : null)
                .update();
        return !done;
    }

    private StepState state(MigrationStep step) {
        return jdbc.sql(SELECT_STATE)
                .param("stepId", step.id())
                .query((rs, i) -> new StepState(rs.getString("status"), rs.getString("checkpoint")))
                .optional()
                .orElse(new StepState(null, null));
    }

    private static long lockId(String stepId) {
        CRC32 crc = new CRC32();
        crc.update(stepId.getBytes(StandardCharsets.UTF_8));
        return LOCK_NAMESPACE + crc.getValue();
    }

    private static String truncate(String text) {
        return text.length() <= LAST_ERROR_MAX ? text : text.substring(0, LAST_ERROR_MAX);
    }

    private record StepState(String status, String checkpoint) {}
}
