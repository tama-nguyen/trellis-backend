package io.trellis.platform.events.application;

import io.trellis.common.annotation.Audited;
import io.trellis.common.annotation.DistributedLock;
import io.trellis.common.annotation.LogExecution;
import io.trellis.common.annotation.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.NotFoundException;
import io.trellis.common.security.PlatformPermissions;
import io.trellis.platform.events.replay.DeadLetterReplayer;
import io.trellis.platform.events.replay.ReadModelRebuilder;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recovery operations, guarded and audited. Also the reference example of stacking the platform annotations:
 * log, authorise, lock per target, transaction, audit row in the transaction.
 */
@Service
public class EventOperations {
    static final String ACTION_DLQ_REPLAYED = "events.dlq_replayed";
    static final String ACTION_READ_MODEL_REBUILT = "read_model.rebuilt";
    static final String AGGREGATE_TOPIC = "EventTopic";
    static final String AGGREGATE_READ_MODEL = "ReadModel";

    private final DeadLetterReplayer replayer;
    private final List<ReadModelRebuilder> rebuilders;

    public EventOperations(DeadLetterReplayer replayer, List<ReadModelRebuilder> rebuilders) {
        this.replayer = replayer;
        this.rebuilders = rebuilders;
    }

    /** Re-publishes up to {@code max} dead letters of {@code eventType}. */
    @LogExecution(logArgs = true)
    @RequiresPermission(anyOf = PlatformPermissions.EVENTS_REPLAY)
    @DistributedLock(key = "#eventType", waitFor = "PT2S", leaseFor = "PT5M")
    @Transactional
    @Audited(action = ACTION_DLQ_REPLAYED, aggregateType = AGGREGATE_TOPIC, aggregateId = "#eventType")
    public int replayDeadLetters(RequestContext ctx, String eventType, int max) {
        return replayer.replay(eventType, max);
    }

    @LogExecution(logArgs = true)
    @RequiresPermission(anyOf = PlatformPermissions.READ_MODEL_REBUILD)
    @DistributedLock(key = "#name", leaseFor = "PT30M")
    @Transactional
    @Audited(action = ACTION_READ_MODEL_REBUILT, aggregateType = AGGREGATE_READ_MODEL, aggregateId = "#name")
    public long rebuildReadModel(RequestContext ctx, String name) {
        return rebuilders.stream()
                .filter(r -> r.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No read model named " + name))
                .rebuild(ctx);
    }

    public List<String> readModelNames() {
        return rebuilders.stream().map(ReadModelRebuilder::name).sorted().toList();
    }
}
