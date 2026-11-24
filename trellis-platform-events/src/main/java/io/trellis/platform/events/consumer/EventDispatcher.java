package io.trellis.platform.events.consumer;

import io.trellis.common.context.Actor;
import io.trellis.common.context.AmbientRequestContext;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.ValidationException;
import io.trellis.common.id.UuidV7;
import io.trellis.common.logging.LogKeys;
import io.trellis.common.retry.Backoff;
import io.trellis.platform.events.EventHeaders;
import io.trellis.platform.events.EventsProperties;
import io.trellis.platform.events.envelope.EventEnvelope;
import io.trellis.platform.events.envelope.EventOriginator;
import io.trellis.platform.events.outbox.TopicResolver;
import java.time.Clock;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Delivers one message to one handler: parse the envelope, set MDC and context, run inbox check plus handler in
 * one transaction, retry with backoff, then apply the handler's {@link FailureMode}. Transport-neutral; the Kafka
 * container and the in-memory broker both call {@link #deliver}.
 */
public final class EventDispatcher {
    private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);
    private static final Set<Class<? extends Throwable>> NON_RETRYABLE = Set.of(
            NonRetryableEventException.class,
            JacksonException.class,
            IllegalArgumentException.class,
            ValidationException.class);

    private final JsonMapper json;
    private final TransactionTemplate tx;
    private final InboxGuard inbox;
    private final DeadLetterPublisher deadLetters;
    private final TopicResolver topics;
    private final EventsProperties.Consumer properties;
    private final Backoff backoff;
    private final String producer;
    private final Clock clock;

    public EventDispatcher(
            JsonMapper json,
            TransactionTemplate tx,
            InboxGuard inbox,
            DeadLetterPublisher deadLetters,
            TopicResolver topics,
            EventsProperties properties,
            Clock clock) {
        this.json = json;
        this.tx = tx;
        this.inbox = inbox;
        this.deadLetters = deadLetters;
        this.topics = topics;
        this.properties = properties.consumer();
        this.backoff = new Backoff(this.properties.baseBackoff(), this.properties.maxBackoff());
        this.producer = properties.producer();
        this.clock = clock;
    }

    /** @throws EventHandlingHaltedException when a THROW-mode handler gives up; the caller must not ack */
    public <T> void deliver(EventHandler<T> handler, IncomingMessage message) {
        EventEnvelope envelope;
        try {
            envelope = json.readValue(message.value(), EventEnvelope.class);
        } catch (JacksonException e) {
            log.atError()
                    .addKeyValue(LogKeys.TOPIC, message.topic())
                    .setCause(e)
                    .log("Unparseable event; dead-lettering");
            deadLetters.publish(message.topic() + EventHeaders.DLQ_SUFFIX, message, handler.consumerName(), e, 1);
            return;
        }
        if (!handler.eventType().equals(envelope.eventType())) {
            return;
        }
        try (var mdc = mdc(handler, envelope, message);
                AmbientRequestContext.Scope scope = AmbientRequestContext.open(contextOf(envelope))) {
            process(handler, envelope, message);
        }
    }

    private <T> void process(EventHandler<T> handler, EventEnvelope envelope, IncomingMessage message) {
        RuntimeException failure = null;
        int attempt = 0;
        while (attempt < properties.maxAttempts()) {
            attempt++;
            try {
                tx.executeWithoutResult(status -> handleOnce(handler, envelope));
                return;
            } catch (RuntimeException e) {
                failure = e;
                if (isNonRetryable(e)) {
                    break;
                }
                log.atWarn().addKeyValue("attempt", attempt).setCause(e).log("Event handler failed; will retry");
                if (attempt < properties.maxAttempts()) {
                    sleep(backoff.delay(attempt - 1).toMillis());
                }
            }
        }
        if (handler.failureMode() == FailureMode.THROW) {
            log.atError().setCause(failure).log("Event handler gave up; halting consumer (THROW mode)");
            throw new EventHandlingHaltedException(handler.consumerName(), failure);
        }
        log.atError().addKeyValue("attempts", attempt).setCause(failure).log("Event handler gave up; dead-lettering");
        deadLetters.publish(
                topics.topicFor(envelope.eventType()) + EventHeaders.DLQ_SUFFIX,
                message,
                handler.consumerName(),
                failure,
                attempt);
    }

    private <T> void handleOnce(EventHandler<T> handler, EventEnvelope envelope) {
        if (envelope.eventVersion() > handler.maxEventVersion()) {
            // Processing a payload we do not understand is worse than processing it late: dead-letter, replay later.
            throw new NonRetryableEventException("Event version " + envelope.eventVersion() + " is newer than "
                    + handler.consumerName() + " understands (" + handler.maxEventVersion() + ")");
        }
        if (!inbox.firstDelivery(handler.consumerName(), envelope.eventId(), envelope.correlationId())) {
            log.debug("Duplicate delivery skipped");
            return;
        }
        T payload = json.treeToValue(envelope.payload(), handler.payloadType());
        handler.handle(contextOf(envelope), new InboundEvent<>(envelope, payload));
    }

    private RequestContext contextOf(EventEnvelope envelope) {
        return RequestContext.builder()
                .actor(actorOf(envelope.originator()))
                .correlationId(
                        envelope.correlationId() != null
                                ? envelope.correlationId()
                                : UuidV7.next(clock).toString())
                .requestTime(clock.instant())
                .build();
    }

    /**
     * The envelope names who caused the change, but carries no authority. A consumer therefore runs with an
     * actor that holds no role and no permission: anything it needs, it must be granted in its own right.
     */
    private Actor actorOf(EventOriginator originator) {
        if (originator == null || originator.id() == null || originator.id().isBlank()) {
            return Actor.system(producer);
        }
        return switch (originator.type()) {
            case USER -> Actor.user(originator.id(), Set.of(), Set.of());
            case SERVICE -> Actor.service(originator.id(), Set.of(), Set.of());
            case SYSTEM -> Actor.system(originator.id());
        };
    }

    private static boolean isNonRetryable(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            for (Class<? extends Throwable> type : NON_RETRYABLE) {
                if (type.isInstance(t)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static MdcScope mdc(EventHandler<?> handler, EventEnvelope envelope, IncomingMessage message) {
        MDC.put(LogKeys.EVENT_ID, String.valueOf(envelope.eventId()));
        MDC.put(LogKeys.EVENT_TYPE, envelope.eventType());
        MDC.put(LogKeys.CONSUMER, handler.consumerName());
        MDC.put(LogKeys.TOPIC, message.topic());
        if (envelope.correlationId() != null) {
            MDC.put(LogKeys.CORRELATION_ID, envelope.correlationId());
        }
        return () -> {
            MDC.remove(LogKeys.EVENT_ID);
            MDC.remove(LogKeys.EVENT_TYPE);
            MDC.remove(LogKeys.CONSUMER);
            MDC.remove(LogKeys.TOPIC);
            MDC.remove(LogKeys.CORRELATION_ID);
        };
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during event retry backoff", e);
        }
    }

    @FunctionalInterface
    private interface MdcScope extends AutoCloseable {
        @Override
        void close();
    }
}
