package io.trellis.common.id;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A reference code: the public, human-friendly identifier people read aloud, type from paper or
 * quote to support, e.g. {@code NT-7K2M-9QXA-4P8R}. A two-to-four letter type prefix, then three
 * groups of four Crockford base32 symbols (60 random bits, see {@link ReferenceCodeGenerator}).
 * Crockford's alphabet drops I, L, O and U, so codes are hard to misread, and {@link #parse}
 * accepts the usual confusions (lower case, O for 0, I/L for 1).
 *
 * <p>Use it for identifiers people see (URLs, support tickets, event payloads). Keep the UUIDv7
 * {@code id} as the primary key; the reference code is a {@code UNIQUE} secondary key.
 *
 * @param value canonical form (upper case, Crockford symbols only)
 */
public record ReferenceCode(String value) {
  /** Crockford base32 symbols in value order. */
  public static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

  static final int GROUPS = 3;
  static final int GROUP_LENGTH = 4;
  static final char SEPARATOR = '-';
  private static final String PREFIX_FORMAT = "[A-Z]{2,4}";
  private static final String GROUP_FORMAT = "[" + ALPHABET + "]{" + GROUP_LENGTH + "}";
  private static final Pattern PREFIX = Pattern.compile("^" + PREFIX_FORMAT + "$");
  private static final Pattern CANONICAL =
      Pattern.compile(
          "^"
              + PREFIX_FORMAT
              + SEPARATOR
              + GROUP_FORMAT
              + SEPARATOR
              + GROUP_FORMAT
              + SEPARATOR
              + GROUP_FORMAT
              + "$");

  public ReferenceCode {
    if (value == null || !CANONICAL.matcher(value).matches()) {
      throw new IllegalArgumentException("Not a canonical reference code: " + value);
    }
  }

  /**
   * Reads user input as a code of type {@code prefix}: trimmed, upper-cased, Crockford
   * substitutions applied ({@code O -> 0}, {@code I, L -> 1}). Empty when the input cannot be such
   * a code.
   */
  public static Optional<ReferenceCode> parse(String prefix, String raw) {
    requirePrefix(prefix);
    if (raw == null) {
      return Optional.empty();
    }
    String upper = raw.trim().toUpperCase(Locale.ROOT);
    String head = prefix + SEPARATOR;
    if (!upper.startsWith(head)) {
      return Optional.empty();
    }
    String body =
        upper.substring(head.length()).replace('O', '0').replace('I', '1').replace('L', '1');
    String candidate = head + body;
    return CANONICAL.matcher(candidate).matches()
        ? Optional.of(new ReferenceCode(candidate))
        : Optional.empty();
  }

  /** The type prefix, e.g. {@code NT}. */
  public String prefix() {
    return value.substring(0, value.indexOf(SEPARATOR));
  }

  public boolean hasPrefix(String prefix) {
    return Objects.equals(prefix(), prefix);
  }

  static String requirePrefix(String prefix) {
    if (prefix == null || !PREFIX.matcher(prefix).matches()) {
      throw new IllegalArgumentException(
          "Reference code prefix must be 2-4 upper-case letters: " + prefix);
    }
    return prefix;
  }

  @Override
  public String toString() {
    return value;
  }
}
