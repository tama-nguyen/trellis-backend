package io.trellis.platform.web.rest;

import io.trellis.common.error.ErrorCode;
import io.trellis.common.error.TrellisException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The aggregate version as a strong ETag ({@code "7"}), and back from {@code If-Match}. */
public final class VersionETags {
  /**
   * Accepts the weak form too: proxies may weaken an ETag, but the version it names remains exact.
   */
  private static final Pattern VERSION_TAG = Pattern.compile("^(?:W/)?\"(\\d{1,18})\"$");

  private VersionETags() {}

  public static String of(long version) {
    return "\"" + version + "\"";
  }

  public static long parse(String ifMatch, ErrorCode invalidPrecondition) {
    Matcher matcher = VERSION_TAG.matcher(ifMatch == null ? "" : ifMatch.trim());
    if (!matcher.matches()) {
      throw new TrellisException(
          invalidPrecondition, "If-Match must be a single ETag such as \"3\"");
    }
    return Long.parseLong(matcher.group(1));
  }
}
