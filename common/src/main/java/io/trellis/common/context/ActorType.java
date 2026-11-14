package io.trellis.common.context;

/** Kind of principal. USER, SERVICE and SYSTEM match the {@code actor.type} enum of the event envelope. */
public enum ActorType {
    USER,
    SERVICE,
    SYSTEM,
    ANONYMOUS
}
