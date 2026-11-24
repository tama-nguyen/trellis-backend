package io.trellis.platform.events.transport;

/**
 * Per-event result of a send. The distinction drives the relay: a rejected row spends an attempt
 * (and may become FAILED); a transport outage defers the row without spending one, so a long broker
 * outage never poisons keys.
 */
public sealed interface SendOutcome {
  record Sent() implements SendOutcome {}

  /** The row itself is the problem (too large, unknown topic, unserialisable). */
  record RowRejected(String error) implements SendOutcome {}

  /** The broker is unreachable or timed out. */
  record TransportDown(String error) implements SendOutcome {}
}
