package io.trellis.platform.aspect.lock;

import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;

/** pollInterval: pause between lock attempts while waiting */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public final class LockProperties {
  @Value("${trellis.lock.poll-interval:PT0.05S}")
  private Duration pollInterval;
}
