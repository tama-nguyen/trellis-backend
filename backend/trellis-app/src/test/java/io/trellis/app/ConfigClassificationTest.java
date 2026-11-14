package io.trellis.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.trellis.platform.security.SensitiveProperties;
import io.trellis.platform.security.SensitiveProperties.Classification;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Security guardrail (plan 08, top 1): every {@code trellis.*} property, whether set in an application YAML or
 * declared by a {@code @ConfigurationProperties} record, must be classified exactly once in {@link
 * SensitiveProperties}. Never disable or skip this test; classify the property instead.
 */
class ConfigClassificationTest {
    private static final String PREFIX = "trellis.";
    private final PathMatchingResourcePatternResolver resources = new PathMatchingResourcePatternResolver();

    @Test
    void everyTrellisPropertyIsClassifiedExactlyOnce() throws IOException {
        Set<String> keys = new TreeSet<>();
        keys.addAll(yamlKeys());
        keys.addAll(metadataKeys());
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
        Set<String> keys = new TreeSet<>(metadataKeys());
        keys.addAll(yamlKeys());
        List<String> stale = SensitiveProperties.SENSITIVE.stream()
                .filter(pattern -> keys.stream()
                        .noneMatch(k -> SensitiveProperties.classify(k) == Classification.SENSITIVE
                                && k.startsWith(pattern.replace("**", "").replace("*", ""))))
                .toList();
        assertTrue(stale.isEmpty(), () -> "SENSITIVE entries without a property (stale or misspelt): " + stale);
    }

    private Set<String> yamlKeys() throws IOException {
        Set<String> keys = new TreeSet<>();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (Resource yaml : resources.getResources("classpath*:application*.yml")) {
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

    private Set<String> metadataKeys() throws IOException {
        Set<String> keys = new TreeSet<>();
        JsonMapper json = JsonMapper.builder().build();
        for (Resource metadata : resources.getResources("classpath*:META-INF/spring-configuration-metadata.json")) {
            try (InputStream in = metadata.getInputStream()) {
                JsonNode properties = json.readTree(in).path("properties");
                for (JsonNode property : properties) {
                    String name = property.path("name").asString();
                    if (name.startsWith(PREFIX)) {
                        keys.add(name);
                    }
                }
            }
        }
        return keys;
    }
}
