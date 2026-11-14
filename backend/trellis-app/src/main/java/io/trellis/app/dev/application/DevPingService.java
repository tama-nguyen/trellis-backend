package io.trellis.app.dev.application;

import io.trellis.common.context.RequestContext;
import io.trellis.common.id.UuidV7;
import io.trellis.platform.events.outbox.OutboxMessage;
import io.trellis.platform.events.outbox.OutboxWriter;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes a {@code dev.ping} outbox row in a transaction, exactly like a business service would. */
@Service
@Profile("dev")
public class DevPingService {
    private final OutboxWriter outbox;

    public DevPingService(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Transactional
    public UUID ping(RequestContext ctx, String message) {
        String aggregateId = UuidV7.next().toString();
        return outbox.append(
                ctx, OutboxMessage.of(DevPing.EVENT_TYPE, DevPing.AGGREGATE_TYPE, aggregateId, new DevPing(message)));
    }
}
