package io.trellis.platform.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Bean belongs to the serving app and is absent from the one-shot setup job. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ConditionalOnProperty(
    prefix = "trellis.setup",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public @interface ConditionalOnServingApp {}
