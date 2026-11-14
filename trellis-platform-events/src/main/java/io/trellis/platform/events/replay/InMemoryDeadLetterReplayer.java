package io.trellis.platform.events.replay;

import io.trellis.platform.events.EventHeaders;
import io.trellis.platform.events.outbox.TopicResolver;
import io.trellis.platform.events.transport.InMemoryEventTransport;
import io.trellis.platform.events.transport.OutboundEvent;
import java.time.Duration;
import java.util.List;

/** Drains the in-memory DLQ topic and re-sends each message to its original topic. */
public final class InMemoryDeadLetterReplayer implements DeadLetterReplayer {
    private final InMemoryEventTransport broker;
    private final TopicResolver topics;

    public InMemoryDeadLetterReplayer(InMemoryEventTransport broker, TopicResolver topics) {
        this.broker = broker;
        this.topics = topics;
    }

    @Override
    public int replay(String eventType, int max) {
        String topic = topics.topicFor(eventType);
        List<OutboundEvent> dead = broker.drain(topic + EventHeaders.DLQ_SUFFIX, max);
        List<OutboundEvent> replays = dead.stream()
                .map(e -> new OutboundEvent(
                        e.eventId(),
                        DeadLetters.originalTopic(e.headers(), topic),
                        e.key(),
                        e.value(),
                        DeadLetters.originalHeaders(e.headers())))
                .toList();
        broker.send(replays, Duration.ZERO);
        return replays.size();
    }
}
