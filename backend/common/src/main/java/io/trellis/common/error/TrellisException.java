package io.trellis.common.error;

import java.util.Map;
import java.util.Objects;

/**
 * Root of every exception the platform maps to a client-facing error.
 *
 * <p>{@link #getMessage()} is shown to the client as the problem {@code detail} unless the category is a
 * server error, so it must never contain secrets, SQL or stack-level details. {@link #properties()} are added
 * to the problem body as extension members and follow the same rule.
 */
public class TrellisException extends RuntimeException {
    private final transient ErrorCode errorCode;
    private final transient Map<String, Object> properties;

    public TrellisException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of(), null);
    }

    public TrellisException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, Map.of(), cause);
    }

    public TrellisException(ErrorCode errorCode, String message, Map<String, Object> properties, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.properties = Map.copyOf(properties);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
