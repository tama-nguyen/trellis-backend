package io.trellis.platform.events.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Who caused the change an event reports. Never carries a credential: an id and a kind, nothing
 * more.
 *
 * <p>It is a separate type from {@code io.trellis.common.context.Actor} on purpose. An {@code
 * Actor} carries the roles and permissions a request was authorized with; none of that belongs on
 * the wire, where it would be a stale copy of authority a consumer must not act on.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventOriginator(OriginatorType type, String id) {}
