package io.trellis.app.dev.application;

/** Payload of the dev-only {@code dev.ping} event (no contract: never leaves a dev machine). */
public record DevPing(String message) {
    public static final String EVENT_TYPE = "dev.ping";
    public static final String AGGREGATE_TYPE = "DevPing";
}
