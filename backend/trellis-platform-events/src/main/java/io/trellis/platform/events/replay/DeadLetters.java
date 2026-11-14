package io.trellis.platform.events.replay;

import io.trellis.platform.events.EventHeaders;
import java.util.Map;
import java.util.stream.Collectors;

/** Helpers shared by the replayers. */
final class DeadLetters {
    private static final String DLQ_HEADER_PREFIX = "x-dlq-";

    private DeadLetters() {}

    /** Original headers without the DLQ diagnostics, so a replayed message looks like the first delivery. */
    static Map<String, String> originalHeaders(Map<String, String> headers) {
        return headers.entrySet().stream()
                .filter(e -> !e.getKey().startsWith(DLQ_HEADER_PREFIX))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    static String originalTopic(Map<String, String> headers, String fallback) {
        return headers.getOrDefault(EventHeaders.DLQ_ORIGINAL_TOPIC, fallback);
    }
}
