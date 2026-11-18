package io.trellis.learningcontent.domain.course.value;

import io.trellis.common.id.ReferenceCode;
import io.trellis.common.id.ReferenceCodeGenerator;
import io.trellis.learningcontent.api.error.CourseErrors;
import java.util.Optional;

/**
 * Public identifier of a course: a reference code {@code CO-XXXX-XXXX-XXXX}. The primary key stays a
 * UUIDv7; this is the id in URLs, event payloads and support conversations.
 *
 * @param value canonical form
 */
public record CourseCode(String value) {
    public static final String PREFIX = "CO";

    public CourseCode {
        if (!new ReferenceCode(value).hasPrefix(PREFIX)) {
            throw new IllegalArgumentException("Not a course code: " + value);
        }
    }

    public static CourseCode generate(ReferenceCodeGenerator generator) {
        return new CourseCode(generator.next().value());
    }

    /** Lenient parse of user input (case, O/0, I/L/1); empty when it cannot be a course code. */
    public static Optional<CourseCode> parse(String input) {
        return ReferenceCode.parse(PREFIX, input).map(code -> new CourseCode(code.value()));
    }

    /** {@link #parse}, or the module's 404 when the input cannot be a course code (it then cannot exist). */
    public static CourseCode parseOrNotFound(String input) {
        return parse(input).orElseThrow(() -> CourseErrors.notFound(String.valueOf(input)));
    }

    @Override
    public String toString() {
        return value;
    }
}
