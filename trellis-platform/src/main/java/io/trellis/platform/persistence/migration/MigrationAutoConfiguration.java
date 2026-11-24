package io.trellis.platform.persistence.migration;

import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two app modes share this wiring:
 *
 * <ul>
 *   <li><b>migrate</b> ({@code trellis.migration.run-on-startup=true}, profile {@code migrate}):
 *       run steps, then exit with status 0 or 1. Deploy it as a pre-release job.
 *   <li><b>serve</b> (default): refuse readiness while a blocking step is pending, optionally run
 *       non-blocking steps in the background.
 * </ul>
 */
@AutoConfiguration
@Import(MigrationProperties.class)
public class MigrationAutoConfiguration {
  private static final Logger log = LoggerFactory.getLogger(MigrationAutoConfiguration.class);

  @Bean
  MigrationRunner migrationRunner(
      List<MigrationStep> steps,
      JdbcClient jdbc,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    return new MigrationRunner(steps, jdbc, new TransactionTemplate(transactionManager), clock);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "trellis.migration",
      name = "run-on-startup",
      havingValue = "true")
  ApplicationRunner migrateModeRunner(
      MigrationRunner runner, MigrationProperties properties, ApplicationContext context) {
    return args -> {
      boolean ok = runner.run(true, properties.includeNonBlocking());
      log.atInfo().addKeyValue("success", ok).log("Migrate mode finished; exiting");
      ExitCodeGenerator exit = () -> ok ? 0 : 1;
      System.exit(SpringApplication.exit(context, exit));
    };
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "trellis.migration",
      name = "run-on-startup",
      havingValue = "false",
      matchIfMissing = true)
  ServeModeMigrationGuard serveModeMigrationGuard(
      MigrationRunner runner,
      MigrationProperties properties,
      ApplicationEventPublisher events,
      TaskExecutor applicationTaskExecutor) {
    return new ServeModeMigrationGuard(runner, properties, events, applicationTaskExecutor);
  }

  /** Readiness gate and optional background runner for the serving app. */
  public static final class ServeModeMigrationGuard {
    private final MigrationRunner runner;
    private final MigrationProperties properties;
    private final ApplicationEventPublisher events;
    private final TaskExecutor executor;

    ServeModeMigrationGuard(
        MigrationRunner runner,
        MigrationProperties properties,
        ApplicationEventPublisher events,
        TaskExecutor executor) {
      this.runner = runner;
      this.properties = properties;
      this.events = events;
      this.executor = executor;
    }

    @EventListener
    public void onReady(ApplicationReadyEvent event) {
      if (properties.enforceBlocking()) {
        List<String> pending = runner.pendingBlockingSteps();
        if (!pending.isEmpty()) {
          log.atError()
              .addKeyValue("steps", pending)
              .log(
                  "Blocking migration steps are not done; refusing traffic. Run the migrate app mode.");
          AvailabilityChangeEvent.publish(events, this, ReadinessState.REFUSING_TRAFFIC);
        }
      }
      if (properties.nonBlockingInBackground()) {
        executor.execute(() -> runner.run(false, true));
      }
    }
  }
}
