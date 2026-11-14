package io.trellis.platform.events.consumer;

import io.trellis.platform.events.envelope.EventEnvelope;

/** A delivered event: envelope metadata plus the payload converted to the handler's type. */
public record InboundEvent<T>(EventEnvelope envelope, T payload) {}
