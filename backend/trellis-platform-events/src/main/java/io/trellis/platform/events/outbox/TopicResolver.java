package io.trellis.platform.events.outbox;

/** Event type to topic: the event type is the logical topic name, optionally prefixed per environment. */
public final class TopicResolver {
    private final String prefix;

    public TopicResolver(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    public String topicFor(String eventType) {
        return prefix + eventType;
    }
}
