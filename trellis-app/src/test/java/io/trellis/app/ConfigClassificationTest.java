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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
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
    Set<String> keys = new TreeSet<>();
    keys.addAll(yamlKeys());
    keys.addAll(valueKeys());
    assertTrue(keys.size() > 10, "Expected to discover trellis.* properties; found " + keys);

    List<String> problems = new ArrayList<>();
    for (String key : keys) {
      Classification c = SensitiveProperties.classify(key);
      if (c == Classification.UNCLASSIFIED || c == Classification.AMBIGUOUS) {
        problems.add(key + " is " + c);
      }
    }
    assertTrue(problems.isEmpty(), () -> "Classify in SensitiveProperties: " + problems);
  }

  @Test
  void everySensitivePatternStillMatchesAProperty() throws IOException {
    Set<String> keys = new TreeSet<>(valueKeys());
    keys.addAll(yamlKeys());
    List<String> stale =
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
    Set<String> keys = new TreeSet<>();
    YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
    List<Resource> yamlFiles = new ArrayList<>();
    for (String pattern : List.of("classpath*:application*.yml", "classpath*:config/*.yml")) {
      yamlFiles.addAll(List.of(resources.getResources(pattern)));
    }
    for (Resource yaml : yamlFiles) {
      for (PropertySource<?> source : loader.load(yaml.getFilename(), yaml)) {
        if (source instanceof EnumerablePropertySource<?> enumerable) {
          for (String name : enumerable.getPropertyNames()) {
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
    Set<String> keys = new TreeSet<>();
    Pattern placeholder = Pattern.compile("\\$\\{(trellis\\.[^:}]+)");
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("io.trellis");
    for (var type : classes) {
      for (var field : type.reflect().getDeclaredFields()) {
        Value value = field.getAnnotation(Value.class);
        if (value == null) {
          continue;
        }
        Matcher matcher = placeholder.matcher(value.value());
        while (matcher.find()) {
          keys.add(matcher.group(1));
        }
      }
    }
    return keys;
  }
}
