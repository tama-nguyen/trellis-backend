package io.trellis.common.validation;

import java.util.Objects;

/**
 * One broken rule.
 *
 * @param field dotted path of the offending input ({@code title}, {@code items[2].qty}); empty for object-level
 * @param code stable machine code ({@code TOO_LONG}); clients translate it
 * @param message developer-facing English hint; never contains the submitted value
 */
public record Violation(String field, String code, String message) {
    public Violation {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
