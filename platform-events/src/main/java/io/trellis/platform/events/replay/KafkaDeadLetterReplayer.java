package io.trellis.platform.events.replay;

import io.trellis.platform.events.EventHeaders;
import io.trellis.platform.events.outbox.TopicResolver;
import io.trellis.platform.events.transport.EventTransport;
import io.trellis.platform.events.transport.OutboundEvent;
import io.trellis.platform.events.transport.SendOutcome;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Reads {@code <topic>.dlq} with its own consumer group, re-sends each record to its original topic, and commits
 * the DLQ offset only after the re-send was acknowledged. A crash in between re-sends again (consumers dedupe).
 */
public final class KafkaDeadLetterReplayer implements DeadLetterReplayer {
    private static final Duration POLL = Duration.ofSeconds(2);
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);
    private static final String GROUP_SUFFIX = "dlq-replayer";

    private final Map<String, Object> consumerConfig;
    private final EventTransport transport;
    private final TopicResolver topics;
    private final String groupPrefix;

    public KafkaDeadLetterReplayer(
            Map<String, Object> consumerConfig, EventTransport transport, TopicResolver topics, String groupPrefix) {
        this.consumerConfig = consumerConfig;
        this.transport = transport;
        this.topics = topics;
        this.groupPrefix = groupPrefix;
    }

    @Override
    public int replay(String eventType, int max) {
        String topic = topics.topicFor(eventType);
        Map<String, Object> config = new HashMap<>(consumerConfig);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupPrefix + GROUP_SUFFIX);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, max);
        int replayed = 0;
        try (KafkaConsumer<String, byte[]> consumer =
                new KafkaConsumer<>(config, new StringDeserializer(), new ByteArrayDeserializer())) {
            consumer.subscribe(List.of(topic + EventHeaders.DLQ_SUFFIX));
            while (replayed < max) {
                ConsumerRecords<String, byte[]> records = consumer.poll(POLL);
                if (records.isEmpty()) {
                    break;
                }
                for (ConsumerRecord<String, byte[]> record : records) {
                    Map<String, String> headers = headersOf(record);
                    OutboundEvent event = new OutboundEvent(
                            null,
                            DeadLetters.originalTopic(headers, topic),
                            record.key(),
                            record.value(),
                            DeadLetters.originalHeaders(headers));
                    SendOutcome outcome =
                            transport.send(List.of(event), SEND_TIMEOUT).getFirst();
                    if (!(outcome instanceof SendOutcome.Sent)) {
                        throw new IllegalStateException("Replay send failed: " + outcome);
                    }
                    replayed++;
                }
                consumer.commitSync();
            }
        }
        return replayed;
    }

    private static Map<String, String> headersOf(ConsumerRecord<String, byte[]> record) {
        Map<String, String> headers = new HashMap<>();
        for (Header header : record.headers()) {
            headers.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
        }
        return headers;
    }
}
