package io.trellis.platform.events.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;

/** {@code actor} of the envelope: type is USER, SYSTEM or SERVICE; never carries a credential. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnvelopeActor(String type, String id) {}
