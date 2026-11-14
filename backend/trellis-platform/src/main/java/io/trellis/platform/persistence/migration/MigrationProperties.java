package io.trellis.platform.persistence.migration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param runOnStartup run the steps and exit; set by the {@code migrate} profile (app mode)
 * @param includeNonBlocking in migrate mode, also run non-blocking steps
 * @param nonBlockingInBackground in the serving app, run non-blocking steps on a background thread
 * @param enforceBlocking refuse readiness while a registered blocking step is not DONE
 */
@ConfigurationProperties(prefix = "trellis.migration")
public record MigrationProperties(
        @DefaultValue("false") boolean runOnStartup,
        @DefaultValue("true") boolean includeNonBlocking,
        @DefaultValue("false") boolean nonBlockingInBackground,
        @DefaultValue("true") boolean enforceBlocking) {}
