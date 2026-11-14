package io.trellis.platform.aspect.lock;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** @param pollInterval pause between lock attempts while waiting */
@ConfigurationProperties(prefix = "trellis.lock")
public record LockProperties(@DefaultValue("PT0.05S") Duration pollInterval) {}
