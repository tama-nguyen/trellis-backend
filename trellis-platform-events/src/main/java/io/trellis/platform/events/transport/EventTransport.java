package io.trellis.platform.events.transport;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * SPI over the message broker. The relay only ever sends one event per partition key in a batch
 * (head of key), so implementations may send a batch concurrently.
 */
public interface EventTransport {

  /**
   * Sends all events, waiting at most {@code timeout} in total. One outcome per input, same order.
   */
  List<SendOutcome> send(List<OutboundEvent> events, Duration timeout);

  /** Short name for logs and metrics ({@code kafka}, {@code inmemory}). */
  String name();

  /** Delivery callback of an in-process subscription; only the in-memory transport supports it. */
  interface Subscriber extends Consumer<OutboundEvent> {}
}
