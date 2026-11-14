package io.trellis.app.fixture.application;

import io.trellis.common.annotation.Audited;
import io.trellis.common.annotation.RequiresPermission;
import io.trellis.common.annotation.RequiresFeature;
import io.trellis.common.annotation.Idempotent;
import io.trellis.common.annotation.DistributedLock;
import io.trellis.common.annotation.LogExecution;
import io.trellis.common.annotation.RateLimited;
import io.trellis.app.config.FeatureFlags;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.PlatformPermissions;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Test fixture carrying every platform annotation, to observe the real advice chain. Registered by the test's own
 * configuration (not a stereotype), so component scanning of other tests never picks it up.
 */
public class OrderProbeService {
    public static final String ACTION = "probe.ran";

    private final List<String> journal;

    public OrderProbeService(List<String> aspectJournal) {
        this.journal = aspectJournal;
    }

    @LogExecution
    @RequiresFeature(flag = FeatureFlags.OPS_API)
    @RateLimited(permits = 100, per = "PT1M")
    @RequiresPermission(anyOf = PlatformPermissions.EVENTS_REPLAY)
    @Idempotent(key = "#key")
    @DistributedLock(key = "#key")
    @Transactional
    @Audited(action = ACTION, aggregateType = "Probe", aggregateId = "#key")
    public ProbeResult probe(RequestContext ctx, String key) {
        journal.add("method(tx=" + TransactionSynchronizationManager.isActualTransactionActive() + ")");
        return new ProbeResult(key, journal.size());
    }

    /** Must round-trip through JSON for @Idempotent replay. */
    public record ProbeResult(String key, int journalSize) {}

    /** Shared mutable journal type, kept separate so tests can clear it. */
    public static final class Journal extends CopyOnWriteArrayList<String> {}
}
