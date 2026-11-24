package io.trellis.platform.events;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Bean is active when {@code trellis.events.transport=inmemory} (the default). */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ConditionalOnProperty(
    prefix = EventsProperties.PREFIX,
    name = "transport",
    havingValue = EventsProperties.TRANSPORT_INMEMORY,
    matchIfMissing = true)
public @interface ConditionalOnInMemoryTransport {}
