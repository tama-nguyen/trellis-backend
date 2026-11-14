package io.trellis.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.trellis.common.id.StableId;
import io.trellis.common.json.CanonicalJson;
import io.trellis.common.retry.Backoff;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract test: Java must produce exactly the values in {@code contracts/testdata/*.json}, which the Python worker
 * also asserts. A failure means the two languages disagree on retry timing or derived ids.
 */
class SharedVectorsTest {
    private static final Path TESTDATA = Path.of(System.getProperty("trellis.contracts.dir"), "testdata");

    @Test
    void backoffMatchesSharedVectors() throws IOException {
        Map<String, Object> root = read("backoff.json");
        for (Object rawSet : list(root.get("parameterSets"))) {
            Map<String, Object> set = map(rawSet);
            Backoff backoff = new Backoff(
                    Duration.ofMillis(number(set.get("baseDelayMs")).longValue()),
                    number(set.get("multiplier")).doubleValue(),
                    Duration.ofMillis(number(set.get("maxDelayMs")).longValue()));
            for (Object rawCase : list(set.get("cases"))) {
                Map<String, Object> c = map(rawCase);
                int attempt = number(c.get("attempt")).intValue();
                double u = number(c.get("u")).doubleValue();
                String label = set.get("name") + " attempt=" + attempt + " u=" + u;
                assertEquals(number(c.get("ceilingMs")).longValue(), backoff.ceilingMillis(attempt), label);
                assertEquals(number(c.get("delayMs")).longValue(), backoff.delayMillis(attempt, u), label);
            }
        }
    }

    @Test
    void stableIdMatchesSharedVectors() throws IOException {
        for (Object rawCase : list(read("stable-id.json").get("cases"))) {
            Map<String, Object> c = map(rawCase);
            Map<String, Object> fields = map(c.get("fields"));
            assertEquals(c.get("canonicalJson"), CanonicalJson.write(fields));
            assertEquals(UUID.fromString((String) c.get("uuid")), StableId.of((String) c.get("namespace"), fields));
        }
    }

    private static Map<String, Object> read(String file) throws IOException {
        return map(CanonicalJson.parse(Files.readString(TESTDATA.resolve(file))));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static List<?> list(Object value) {
        return (List<?>) value;
    }

    private static BigDecimal number(Object value) {
        return (BigDecimal) value;
    }
}
