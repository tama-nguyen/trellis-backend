package io.trellis.platform.persistence.migration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;

/**
 * runOnStartup: run the steps and exit; set by the {@code migrate} profile (app mode)
 * includeNonBlocking: in migrate mode, also run non-blocking steps nonBlockingInBackground: in the
 * serving app, run non-blocking steps on a background thread enforceBlocking: refuse readiness
 * while a registered blocking step is not DONE
 */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public final class MigrationProperties {
  @Value("${trellis.migration.run-on-startup:false}")
  private boolean runOnStartup;

  @Value("${trellis.migration.include-non-blocking:true}")
  private boolean includeNonBlocking;

  @Value("${trellis.migration.non-blocking-in-background:false}")
  private boolean nonBlockingInBackground;

  @Value("${trellis.migration.enforce-blocking:true}")
  private boolean enforceBlocking;
}
