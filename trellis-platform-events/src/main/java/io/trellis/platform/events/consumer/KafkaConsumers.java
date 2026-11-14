package io.trellis.platform.events.consumer;

import io.trellis.platform.events.EventsProperties;
import io.trellis.platform.events.outbox.TopicResolver;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * One listener container per handler, group {@code <groupPrefix><consumerName>}, so a slow handler never delays
 * another. Offsets are committed per record after the handler (and its transaction) returned. Container
 * observation is on, so the consumer span continues the producer's {@code traceparent}.
 *
 * <p>Errors that escape the dispatcher: for CONTINUE handlers only a failed DLQ write, retried forever with
 * backoff (never skip a message we could not park); for THROW handlers the container stops.
 */
public final class KafkaConsumers implements SmartLifecycle {
    private static final long DLQ_RETRY_INITIAL_MS = 1_000;
    private static final long DLQ_RETRY_MAX_MS = 60_000;

    private final Map<String, Object> consumerConfig;
    private final EventDispatcher dispatcher;
    private final List<EventHandler<?>> handlers;
    private final TopicResolver topics;
    private final EventsProperties.Consumer properties;
    private final List<ConcurrentMessageListenerContainer<String, byte[]>> containers = new ArrayList<>();
    private volatile boolean running;

    public KafkaConsumers(
            Map<String, Object> consumerConfig,
            EventDispatcher dispatcher,
            List<EventHandler<?>> handlers,
            TopicResolver topics,
            EventsProperties.Consumer properties) {
        this.consumerConfig = consumerConfig;
        this.dispatcher = dispatcher;
        this.handlers = handlers;
        this.topics = topics;
        this.properties = properties;
    }

    @Override
    public void start() {
        for (EventHandler<?> handler : handlers) {
            if (handler.isEnabled()) {
                containers.add(containerFor(handler));
            }
        }
        containers.forEach(ConcurrentMessageListenerContainer::start);
        running = true;
    }

    private ConcurrentMessageListenerContainer<String, byte[]> containerFor(EventHandler<?> handler) {
        Map<String, Object> config = new HashMap<>(consumerConfig);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, properties.groupPrefix() + handler.consumerName());
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var factory = new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), new ByteArrayDeserializer());

        ContainerProperties containerProperties = new ContainerProperties(topics.topicFor(handler.eventType()));
        containerProperties.setAckMode(ContainerProperties.AckMode.RECORD);
        containerProperties.setObservationEnabled(true);
        containerProperties.setMessageListener(
                (MessageListener<String, byte[]>) record -> dispatcher.deliver(handler, toMessage(record)));

        var container = new ConcurrentMessageListenerContainer<>(factory, containerProperties);
        container.setBeanName("trellis-consumer-" + handler.consumerName());
        if (handler.failureMode() == FailureMode.THROW) {
            container.setCommonErrorHandler(new CommonContainerStoppingErrorHandler());
        } else {
            ExponentialBackOff backOff = new ExponentialBackOff(DLQ_RETRY_INITIAL_MS, 2.0);
            backOff.setMaxInterval(DLQ_RETRY_MAX_MS);
            container.setCommonErrorHandler(new DefaultErrorHandler(backOff));
        }
        return container;
    }

    private static IncomingMessage toMessage(ConsumerRecord<String, byte[]> record) {
        Map<String, String> headers = new HashMap<>();
        for (Header header : record.headers()) {
            headers.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
        }
        return new IncomingMessage(
                record.topic(), record.partition(), record.offset(), record.key(), record.value(), headers);
    }

    @Override
    public void stop() {
        containers.forEach(ConcurrentMessageListenerContainer::stop);
        containers.clear();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Start after everything else (relay, web), stop first. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
