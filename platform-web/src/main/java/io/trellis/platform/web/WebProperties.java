package io.trellis.platform.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param requestLog log one line per request (method, path, status, duration); never logs query or body
 * @param correlationIdPattern accepted inbound X-Correlation-Id; anything else is replaced by a fresh id
 */
@ConfigurationProperties(prefix = "trellis.web")
public record WebProperties(
        @DefaultValue("true") boolean requestLog,
        @DefaultValue("^[A-Za-z0-9][A-Za-z0-9._:-]{7,99}$") String correlationIdPattern) {}
