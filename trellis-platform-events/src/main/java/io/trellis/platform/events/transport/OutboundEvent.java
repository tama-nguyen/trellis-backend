package io.trellis.platform.events.transport;

import java.util.Map;
import java.util.UUID;

/** A serialised event ready for the broker. {@code key} is the partition key. */
public record OutboundEvent(
    UUID eventId, String topic, String key, byte[] value, Map<String, String> headers) {
  public OutboundEvent {
    headers = Map.copyOf(headers);
  }
}
