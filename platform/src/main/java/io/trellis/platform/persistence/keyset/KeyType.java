package io.trellis.platform.persistence.keyset;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

/** How a sort key travels through the cursor (always as a string) and back into a SQL parameter. */
public enum KeyType {
    TIMESTAMP(text -> java.sql.Timestamp.from(Instant.parse(text))),
    UUID_KEY(UUID::fromString),
    LONG(Long::valueOf),
    STRING(text -> text);

    private final Function<String, Object> parser;

    KeyType(Function<String, Object> parser) {
        this.parser = parser;
    }

    Object toParameter(String text) {
        return parser.apply(text);
    }
}
