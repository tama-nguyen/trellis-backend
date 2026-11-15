package io.trellis.course.web.rest;

import io.trellis.common.error.TrellisException;
import io.trellis.course.api.error.CourseErrorCode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The aggregate version as a strong ETag ({@code "7"}), and back from {@code If-Match}. */
final class VersionETags {
    /** Accepts the weak form too: proxies may weaken an ETag, the version it names is still exact. */
    private static final Pattern VERSION_TAG = Pattern.compile("^(?:W/)?\"(\\d{1,18})\"$");

    private VersionETags() {}

    static String of(long version) {
        return "\"" + version + "\"";
    }

    static long parse(String ifMatch) {
        Matcher matcher = VERSION_TAG.matcher(ifMatch == null ? "" : ifMatch.trim());
        if (!matcher.matches()) {
            throw new TrellisException(
                    CourseErrorCode.COURSE_INVALID_PRECONDITION, "If-Match must be a single ETag such as \"3\"");
        }
        return Long.parseLong(matcher.group(1));
    }
}
