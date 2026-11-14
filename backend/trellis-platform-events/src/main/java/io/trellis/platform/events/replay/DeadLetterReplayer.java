package io.trellis.platform.events.replay;

/** Moves messages from {@code <topic>.dlq} back to their original topic. Consumers dedupe through the inbox. */
public interface DeadLetterReplayer {
    /** @return number of messages re-published */
    int replay(String eventType, int max);
}
