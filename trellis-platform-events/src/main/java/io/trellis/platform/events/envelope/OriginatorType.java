package io.trellis.platform.events.envelope;

/**
 * Kinds of originator an envelope may name. Matches the {@code actor.type} enum of {@code
 * contracts/events/envelope.schema.json}, so the values are fixed by the contract.
 */
public enum OriginatorType {
  USER,
  SYSTEM,
  SERVICE
}
