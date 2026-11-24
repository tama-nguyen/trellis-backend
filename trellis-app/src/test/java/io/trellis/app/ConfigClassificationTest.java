package io.trellis.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.trellis.platform.security.SensitiveProperties;
import io.trellis.platform.security.SensitiveProperties.Classification;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Security guardrail (plan 08, top 1): every {@code trellis.*} property, whether set in an
 * application YAML or declared by a Spring {@code @Value} placeholder, must be classified exactly
 * once in {@link SensitiveProperties}. Never disable or skip this test; classify the property
 * instead.
 */
class ConfigClassificationTest {
  private static final String PREFIX = "trellis.";
  private final PathMatchingResourcePatternResolver resources =
      new PathMatchingResourcePatternResolver();

  @Test
  void everyTrellisPropertyIsClassifiedExactlyOnce() throws IOException {
    val keys = new TreeSet<String>();
    keys.addAll(yamlKeys());
    keys.addAll(valueKeys());
    assertTrue(keys.size() > 10, "Expected to discover trellis.* properties; found " + keys);

    val problems = new ArrayList<String>();
    for (val key : keys) {
      val classification = SensitiveProperties.classify(key);
      if (classification == Classification.UNCLASSIFIED
          || classification == Classification.AMBIGUOUS) {
        problems.add(key + " is " + classification);
      }
    }
    assertTrue(problems.isEmpty(), () -> "Classify in SensitiveProperties: " + problems);
  }

  @Test
  void everySensitivePatternStillMatchesAProperty() throws IOException {
    val keys = new TreeSet<>(valueKeys());
    keys.addAll(yamlKeys());
    val stale =
        SensitiveProperties.SENSITIVE.stream()
            .filter(
                pattern ->
                    keys.stream()
                        .noneMatch(
                            k ->
                                SensitiveProperties.classify(k) == Classification.SENSITIVE
                                    && k.startsWith(pattern.replace("**", "").replace("*", ""))))
            .toList();
    assertTrue(
        stale.isEmpty(),
        () -> "SENSITIVE entries without a property (stale or misspelt): " + stale);
  }

  private Set<String> yamlKeys() throws IOException {
    val keys = new TreeSet<String>();
    val loader = new YamlPropertySourceLoader();
    val yamlFiles = new ArrayList<Resource>();
    for (val pattern : List.of("classpath*:application*.yml", "classpath*:config/*.yml")) {
      yamlFiles.addAll(List.of(resources.getResources(pattern)));
    }
    for (val yaml : yamlFiles) {
      for (val source : loader.load(yaml.getFilename(), yaml)) {
        if (source instanceof EnumerablePropertySource<?> enumerable) {
          for (val name : enumerable.getPropertyNames()) {
            if (name.startsWith(PREFIX)) {
              keys.add(name);
            }
          }
        }
      }
    }
    return keys;
  }

  private Set<String> valueKeys() {
    val keys = new TreeSet<String>();
    val placeholder = Pattern.compile("\\$\\{(trellis\\.[^:}]+)");
    val classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("io.trellis");
    for (val type : classes) {
      for (val field : type.reflect().getDeclaredFields()) {
        val value = field.getAnnotation(Value.class);
        if (value == null) {
          continue;
        }
        val matcher = placeholder.matcher(value.value());
        while (matcher.find()) {
          keys.add(matcher.group(1));
        }
      }
    }
    return keys;
  }
}
