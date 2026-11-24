package io.trellis.app;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
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
      String profile, String changelog, String store, boolean introspection) {
    var environment = environment(profile);
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
              .filter(java.util.Objects::nonNull)
              .findFirst()
              .orElseThrow());
    }
  }

  @ParameterizedTest
  @CsvSource({
    "dev,setup", "test,setup", "prod,setup",
    "dev,migrate", "test,migrate", "prod,migrate"
  })
  void operationalProfileOverridesEnvironment(String profile, String mode) {
    var environment = environment(profile + "," + mode);
    assertEquals("none", environment.getProperty("spring.main.web-application-type"));
    assertFalse(environment.getProperty("trellis.events.relay.enabled", Boolean.class));
    assertTrue(
        environment.getProperty(
            mode.equals("setup") ? "trellis.setup.enabled" : "trellis.migration.run-on-startup",
            Boolean.class));
    assertTrue(
        environment
            .getRequiredProperty("spring.liquibase.change-log")
            .endsWith("db.changelog-" + profile + ".yaml"));
  }

  private StandardEnvironment environment(String profiles) {
    var environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource("test-profiles", Map.of("spring.profiles.active", profiles)));
    ConfigDataEnvironmentPostProcessor.applyTo(environment);
    return environment;
  }
}
