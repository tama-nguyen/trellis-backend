package io.trellis.platform.events.consumer;

import io.trellis.platform.events.EventHeaders;
import io.trellis.platform.events.transport.EventTransport;
import io.trellis.platform.events.transport.OutboundEvent;
import io.trellis.platform.events.transport.SendOutcome;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes a failed message, byte for byte, to {@code <topic>.dlq} with headers saying where it came
 * from and why. Throws if the DLQ write fails, so the transport redelivers instead of losing the
 * message.
 */
public final class DeadLetterPublisher {
  private static final int ERROR_MAX = 1000;

  private final EventTransport transport;
  private final Duration timeout;
  private final Clock clock;

  public DeadLetterPublisher(EventTransport transport, Duration timeout, Clock clock) {
    this.transport = transport;
    this.timeout = timeout;
    this.clock = clock;
  }

  public void publish(
      String dlqTopic,
      IncomingMessage message,
      String consumerName,
      Throwable error,
      int attempts) {
    Map<String, String> headers = new LinkedHashMap<>(message.headers());
    headers.put(EventHeaders.DLQ_ORIGINAL_TOPIC, message.topic());
    headers.put(EventHeaders.DLQ_ORIGINAL_PARTITION, String.valueOf(message.partition()));
    headers.put(EventHeaders.DLQ_ORIGINAL_OFFSET, String.valueOf(message.offset()));
    headers.put(EventHeaders.DLQ_CONSUMER, consumerName);
    headers.put(EventHeaders.DLQ_EXCEPTION, error.getClass().getSimpleName());
    headers.put(EventHeaders.DLQ_ERROR, truncate(String.valueOf(error.getMessage())));
    headers.put(EventHeaders.DLQ_ATTEMPTS, String.valueOf(attempts));
    headers.put(EventHeaders.DLQ_FAILED_AT, clock.instant().toString());
    UUID eventId = parseEventId(message.headers().get(EventHeaders.EVENT_ID));
    OutboundEvent dead =
        new OutboundEvent(eventId, dlqTopic, message.key(), message.value(), headers);
    SendOutcome outcome = transport.send(List.of(dead), timeout).getFirst();
    if (!(outcome instanceof SendOutcome.Sent)) {
      throw new IllegalStateException("Dead-letter write to " + dlqTopic + " failed: " + outcome);
    }
  }

  private static UUID parseEventId(String value) {
    try {
      return value == null ? null : UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String truncate(String text) {
    return text.length() <= ERROR_MAX ? text : text.substring(0, ERROR_MAX);
  }
}
