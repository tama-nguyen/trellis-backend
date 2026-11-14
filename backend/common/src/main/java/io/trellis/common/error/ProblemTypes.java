package io.trellis.common.error;

/**
 * Values of the problem {@code type} member: one URN per {@link ErrorCategory} family, not per error code (the
 * code is in the {@code code} member). The web client relies on these exact strings; for example it shows the
 * server's {@code detail} only when the type is {@link #VALIDATION}.
 */
public final class ProblemTypes {
    public static final String PREFIX = "urn:trellis:problem:";
    public static final String VALIDATION = PREFIX + "validation";
    /** Request shape problems that are not field validation: 405, 406, 413, 415. */
    public static final String BAD_REQUEST = PREFIX + "bad-request";

    public static final String UNAUTHENTICATED = PREFIX + "unauthenticated";
    public static final String FORBIDDEN = PREFIX + "forbidden";
    public static final String NOT_FOUND = PREFIX + "not-found";
    public static final String CONFLICT = PREFIX + "conflict";
    public static final String RATE_LIMITED = PREFIX + "rate-limited";
    public static final String UNAVAILABLE = PREFIX + "unavailable";
    public static final String INTERNAL = PREFIX + "internal";

    /** Media type of the problem body. */
    public static final String MEDIA_TYPE = "application/problem+json";

    private ProblemTypes() {}
}
