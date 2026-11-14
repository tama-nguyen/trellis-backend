package io.trellis.platform.events;

import io.micrometer.core.instrument.MeterRegistry;
import io.trellis.common.context.TraceContextAccessor;
import io.trellis.platform.events.application.EventOperations;
import io.trellis.platform.events.consumer.DeadLetterPublisher;
import io.trellis.platform.events.consumer.EventDispatcher;
import io.trellis.platform.events.consumer.EventHandler;
import io.trellis.platform.events.consumer.InMemoryConsumers;
import io.trellis.platform.events.consumer.InboxGuard;
import io.trellis.platform.events.consumer.JdbcInboxGuard;
import io.trellis.platform.events.consumer.KafkaConsumers;
import io.trellis.platform.events.outbox.JdbcOutboxWriter;
import io.trellis.platform.events.outbox.OutboxRelays;
import io.trellis.platform.events.outbox.OutboxWriter;
import io.trellis.platform.events.outbox.TopicResolver;
import io.trellis.platform.events.replay.DeadLetterReplayer;
import io.trellis.platform.events.replay.InMemoryDeadLetterReplayer;
import io.trellis.platform.events.replay.KafkaDeadLetterReplayer;
import io.trellis.platform.events.replay.ReadModelRebuilder;
import io.trellis.platform.events.transport.EventTransport;
import io.trellis.platform.events.transport.InMemoryEventTransport;
import io.trellis.platform.events.transport.KafkaEventTransport;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Event platform wiring. Transport-specific beans sit in the two nested configurations. */
@AutoConfiguration(before = KafkaAutoConfiguration.class)
@EnableConfigurationProperties(EventsProperties.class)
@Import({
    EventsAutoConfiguration.InMemoryTransportConfiguration.class,
    EventsAutoConfiguration.KafkaTransportConfiguration.class,
    OutboxRelays.class
})
public class EventsAutoConfiguration {

    @Bean
    TopicResolver topicResolver(EventsProperties properties) {
        return new TopicResolver(properties.topicPrefix());
    }

    @Bean
    OutboxWriter outboxWriter(
            JdbcClient jdbc,
            JsonMapper json,
            TopicResolver topics,
            ObjectProvider<TraceContextAccessor> trace,
            EventsProperties properties,
            Clock clock) {
        return new JdbcOutboxWriter(
                jdbc, json, topics, trace.getIfAvailable(() -> TraceContextAccessor.NOOP), properties, clock);
    }

    @Bean
    InboxGuard inboxGuard(JdbcClient jdbc, MeterRegistry meters, Clock clock) {
        return new JdbcInboxGuard(jdbc, meters, clock);
    }

    @Bean
    DeadLetterPublisher deadLetterPublisher(EventTransport transport, EventsProperties properties, Clock clock) {
        return new DeadLetterPublisher(transport, properties.relay().sendTimeout(), clock);
    }

    @Bean
    EventDispatcher eventDispatcher(
            JsonMapper json,
            PlatformTransactionManager transactionManager,
            InboxGuard inbox,
            DeadLetterPublisher deadLetters,
            TopicResolver topics,
            EventsProperties properties,
            Clock clock) {
        return new EventDispatcher(
                json, new TransactionTemplate(transactionManager), inbox, deadLetters, topics, properties, clock);
    }

    @Bean
    EventOperations eventOperations(DeadLetterReplayer replayer, List<ReadModelRebuilder> rebuilders) {
        return new EventOperations(replayer, rebuilders);
    }

    /** {@code trellis.events.transport=inmemory}: single process, nothing to run. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnInMemoryTransport
    static class InMemoryTransportConfiguration {
        @Bean
        InMemoryEventTransport inMemoryEventTransport() {
            return new InMemoryEventTransport();
        }

        @Bean
        InMemoryConsumers inMemoryConsumers(
                InMemoryEventTransport broker,
                EventDispatcher dispatcher,
                List<EventHandler<?>> handlers,
                TopicResolver topics) {
            return new InMemoryConsumers(broker, dispatcher, handlers, topics);
        }

        @Bean
        DeadLetterReplayer inMemoryDeadLetterReplayer(InMemoryEventTransport broker, TopicResolver topics) {
            return new InMemoryDeadLetterReplayer(broker, topics);
        }
    }

    /** {@code trellis.events.transport=kafka}. Connection settings come from {@code spring.kafka.*}. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnKafkaTransport
    static class KafkaTransportConfiguration {
        // Fail a send within seconds when the cluster is unreachable, so the relay defers instead of hanging.
        private static final int MAX_BLOCK_MS = 5_000;
        private static final int REQUEST_TIMEOUT_MS = 10_000;
        private static final int DELIVERY_TIMEOUT_MS = 30_000;

        @Bean
        KafkaTemplate<String, byte[]> trellisEventKafkaTemplate(KafkaProperties kafka) {
            Map<String, Object> config = new HashMap<>(kafka.buildProducerProperties());
            config.put(ProducerConfig.ACKS_CONFIG, "all");
            config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
            config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, MAX_BLOCK_MS);
            config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, REQUEST_TIMEOUT_MS);
            config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, DELIVERY_TIMEOUT_MS);
            return new KafkaTemplate<>(
                    new DefaultKafkaProducerFactory<>(config, new StringSerializer(), new ByteArraySerializer()));
        }

        @Bean
        KafkaEventTransport kafkaEventTransport(KafkaTemplate<String, byte[]> trellisEventKafkaTemplate) {
            return new KafkaEventTransport(trellisEventKafkaTemplate);
        }

        @Bean
        KafkaConsumers kafkaConsumers(
                KafkaProperties kafka,
                EventDispatcher dispatcher,
                List<EventHandler<?>> handlers,
                TopicResolver topics,
                EventsProperties properties) {
            return new KafkaConsumers(
                    kafka.buildConsumerProperties(), dispatcher, handlers, topics, properties.consumer());
        }

        @Bean
        DeadLetterReplayer kafkaDeadLetterReplayer(
                KafkaProperties kafka, EventTransport transport, TopicResolver topics, EventsProperties properties) {
            return new KafkaDeadLetterReplayer(
                    kafka.buildConsumerProperties(),
                    transport,
                    topics,
                    properties.consumer().groupPrefix());
        }
    }
}
