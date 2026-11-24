package io.trellis.platform.events;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Bean is active when {@code trellis.events.relay.enabled=true} (the default). */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ConditionalOnProperty(
    prefix = EventsProperties.PREFIX + ".relay",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public @interface ConditionalOnOutboxRelay {}
