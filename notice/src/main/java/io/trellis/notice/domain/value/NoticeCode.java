package io.trellis.notice.domain.value;

import io.trellis.common.id.ReferenceCode;
import io.trellis.common.id.ReferenceCodeGenerator;
import io.trellis.notice.api.error.NoticeErrors;
import java.util.Optional;

/**
 * Public identifier of a notice: a reference code {@code NT-XXXX-XXXX-XXXX}. The primary key stays a UUIDv7.
 *
 * @param value canonical form
 */
public record NoticeCode(String value) {
    public static final String PREFIX = "NT";

    public NoticeCode {
        if (!new ReferenceCode(value).hasPrefix(PREFIX)) {
            throw new IllegalArgumentException("Not a notice code: " + value);
        }
    }

    public static NoticeCode generate(ReferenceCodeGenerator generator) {
        return new NoticeCode(generator.next().value());
    }

    /** Lenient parse of user input (case, O/0, I/L/1); empty when it cannot be a notice code. */
    public static Optional<NoticeCode> parse(String input) {
        return ReferenceCode.parse(PREFIX, input).map(code -> new NoticeCode(code.value()));
    }

    /** {@link #parse}, or the module's 404 when the input cannot be a notice code (it then cannot exist). */
    public static NoticeCode parseOrNotFound(String input) {
        return parse(input).orElseThrow(() -> NoticeErrors.notFound(String.valueOf(input)));
    }

    @Override
    public String toString() {
        return value;
    }
}
