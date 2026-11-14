package io.trellis.platform.events.consumer;

import java.util.Map;

/** Raw message as received from a transport. {@code partition}/{@code offset} are -1 for the in-memory broker. */
public record IncomingMessage(
        String topic, int partition, long offset, String key, byte[] value, Map<String, String> headers) {
    public IncomingMessage {
        headers = Map.copyOf(headers);
    }
}
