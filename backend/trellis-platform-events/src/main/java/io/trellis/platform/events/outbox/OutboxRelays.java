package io.trellis.platform.events.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import io.trellis.platform.events.ConditionalOnOutboxRelay;
import io.trellis.platform.events.EventsProperties;
import io.trellis.platform.events.transport.EventTransport;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Relay and maintenance beans; package-level so the relay internals stay package-private. */
@Configuration(proxyBeanMethods = false)
public class OutboxRelays {

    @Bean
    @ConditionalOnOutboxRelay
    OutboxRelay outboxRelay(
            JdbcClient jdbc,
            PlatformTransactionManager transactionManager,
            EventTransport transport,
            JsonMapper json,
            EventsProperties properties,
            MeterRegistry meters,
            Clock clock) {
        return new OutboxRelay(
                jdbc,
                new TransactionTemplate(transactionManager),
                transport,
                new EnvelopeCodec(json, properties.producer()),
                properties.relay(),
                meters,
                clock);
    }

    @Bean
    OutboxMaintenance outboxMaintenance(
            JdbcClient jdbc, EventsProperties properties, MeterRegistry meters, Clock clock) {
        return new OutboxMaintenance(jdbc, properties.retention(), meters, clock);
    }
}
