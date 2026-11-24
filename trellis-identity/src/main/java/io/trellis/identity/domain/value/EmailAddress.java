package io.trellis.identity.domain.value;

import java.util.Locale;
import java.util.Objects;

/**
 * A login address in its canonical form. Sign-in, registration and lookup must agree on one spelling, so the
 * value is trimmed and lower-cased once here instead of at every call site.
 */
public record EmailAddress(String value) {
    public EmailAddress {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
    }

    public static EmailAddress of(String raw) {
        return new EmailAddress(raw.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
