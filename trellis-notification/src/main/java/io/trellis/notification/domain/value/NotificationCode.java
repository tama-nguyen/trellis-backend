package io.trellis.notification.domain.value;

import io.trellis.common.id.ReferenceCode;
import io.trellis.common.id.ReferenceCodeGenerator;
import io.trellis.notification.api.error.NotificationErrors;
import java.util.Optional;

/**
 * Public identifier of a notification: a reference code {@code NT-XXXX-XXXX-XXXX}. The primary key
 * stays a UUIDv7.
 *
 * @param value canonical form
 */
public record NotificationCode(String value) {
  public static final String PREFIX = "NT";

  public NotificationCode {
    if (!new ReferenceCode(value).hasPrefix(PREFIX)) {
      throw new IllegalArgumentException("Not a notification code: " + value);
    }
  }

  public static NotificationCode generate(ReferenceCodeGenerator generator) {
    return new NotificationCode(generator.next().value());
  }

  /**
   * Lenient parse of user input (case, O/0, I/L/1); empty when it cannot be a notification code.
   */
  public static Optional<NotificationCode> parse(String input) {
    return ReferenceCode.parse(PREFIX, input).map(code -> new NotificationCode(code.value()));
  }

  /**
   * {@link #parse}, or the module's 404 when the input cannot be a notification code (it then
   * cannot exist).
   */
  public static NotificationCode parseOrNotFound(String input) {
    return parse(input).orElseThrow(() -> NotificationErrors.notFound(String.valueOf(input)));
  }

  @Override
  public String toString() {
    return value;
  }
}
