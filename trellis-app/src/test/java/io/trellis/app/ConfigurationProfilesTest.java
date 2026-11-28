package io.trellis.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.trellis.app.setup.SetupProperties;
import io.trellis.identity.infrastructure.mail.IdentityMailWorker;
import io.trellis.platform.events.ConditionalOnEventConsumers;
import io.trellis.platform.events.ConditionalOnOutboxRelay;
import java.util.Map;
import java.util.Objects;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class ConfigurationProfilesTest {
  @ParameterizedTest
  @CsvSource({
    "dev,db.changelog-dev.yaml,memory,true",
    "test,db.changelog-test.yaml,memory,false",
    "prod,db.changelog-prod.yaml,redis,false"
  })
  void environmentOverridesImportedDefaults(
      final String profile,
      final String changelog,
      final String store,
      final boolean introspection) {
    // When
    val environment = environment(profile);

    // Then
    assertTrue(environment.getRequiredProperty("spring.liquibase.change-log").endsWith(changelog));
    assertEquals(store, environment.getProperty("trellis.rate-limit.store"));
    assertEquals(
        introspection,
        environment.getProperty("spring.graphql.schema.introspection.enabled", Boolean.class));
    assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
    assertEquals(
        "1/PT1M,3/PT1H", environment.getProperty("trellis.rate-limit.policies.identity-forgot"));
    assertFalse(environment.getProperty("spring.jpa.open-in-view", Boolean.class));
    if (profile.equals("test")) {
      assertFalse(environment.containsProperty("spring.datasource.url"));
    }
    if (profile.equals("prod")) {
      // Inspect raw placeholders so the test needs no production secrets.
      assertEquals(
          "${TRELLIS_DB_PASSWORD}",
          environment.getPropertySources().stream()
              .filter(source -> source.getName().contains("application-prod.yml"))
              .map(source -> source.getProperty("spring.datasource.password"))
              .filter(Objects::nonNull)
              .findFirst()
              .orElseThrow());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"true", "false"})
  void productionSchemaChangesOnlyInTheSetupJob(final String setup) {
    // Given
    val environment = environment("prod");
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("deployment", Map.of("trellis.setup.enabled", setup)));

    // When & Then
    assertEquals(setup, environment.getProperty("spring.liquibase.enabled"));
  }

  @Test
  void setupJobStartsNoRelayConsumerOrMailWorker() {
    new ApplicationContextRunner()
        .withUserConfiguration(WorkerFixtures.class, IdentityMailWorker.class)
        .withPropertyValues("trellis.setup.enabled=true")
        .run(
            context -> {
              assertNull(context.getStartupFailure());
              assertFalse(context.containsBean("relay"));
              assertFalse(context.containsBean("consumers"));
              assertEquals(0, context.getBeanNamesForType(IdentityMailWorker.class).length);
            });
  }

  @Test
  void servingAppStartsTheRelayAndConsumers() {
    new ApplicationContextRunner()
        .withUserConfiguration(WorkerFixtures.class)
        .run(
            context -> {
              assertTrue(context.containsBean("relay"));
              assertTrue(context.containsBean("consumers"));
            });
  }

  @ParameterizedTest
  @CsvSource({"true,true", "FALSE,true", "1,false", "0,false", "yes,false", "'',false"})
  void setupSwitchAcceptsOnlyTrueOrFalse(final String value, final boolean accepted) {
    new ApplicationContextRunner()
        .withInitializer(
            context ->
                context
                    .getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
        .withUserConfiguration(SetupProperties.class)
        .withPropertyValues("trellis.setup.enabled=" + value)
        .run(context -> assertEquals(accepted, context.getStartupFailure() == null));
  }

  @Configuration(proxyBeanMethods = false)
  static class WorkerFixtures {
    @Bean
    @ConditionalOnOutboxRelay
    Object relay() {
      return new Object();
    }

    @Bean
    @ConditionalOnEventConsumers
    Object consumers() {
      return new Object();
    }
  }

  private StandardEnvironment environment(final String profiles) {
    val environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource("test-profiles", Map.of("spring.profiles.active", profiles)));
    ConfigDataEnvironmentPostProcessor.applyTo(environment);
    return environment;
  }
}
