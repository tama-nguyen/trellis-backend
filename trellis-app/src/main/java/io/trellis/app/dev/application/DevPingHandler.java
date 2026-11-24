package io.trellis.app.dev.application;

import io.trellis.common.context.RequestContext;
import io.trellis.platform.events.consumer.EventHandler;
import io.trellis.platform.events.consumer.InboundEvent;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Receives {@code dev.ping} and remembers the last one, so a developer can see the round trip. */
@Component
@Profile("dev")
public class DevPingHandler implements EventHandler<DevPing> {
  private static final Logger log = LoggerFactory.getLogger(DevPingHandler.class);

  private final AtomicLong received = new AtomicLong();
  private final AtomicReference<UUID> lastEventId = new AtomicReference<>();

  @Override
  public String consumerName() {
    return "dev-ping-logger";
  }

  @Override
  public String eventType() {
    return DevPing.EVENT_TYPE;
  }

  @Override
  public Class<DevPing> payloadType() {
    return DevPing.class;
  }

  @Override
  public void handle(final RequestContext ctx, final InboundEvent<DevPing> event) {
    received.incrementAndGet();
    lastEventId.set(event.envelope().eventId());
    log.atInfo().addKeyValue("message", event.payload().message()).log("dev.ping received");
  }

  public long received() {
    return received.get();
  }

  public UUID lastEventId() {
    return lastEventId.get();
  }
}
