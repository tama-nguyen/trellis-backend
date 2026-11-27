package io.trellis.notification.application.validation;

import io.trellis.common.context.RequestContext;
import io.trellis.common.validation.CommandValidator;
import io.trellis.common.validation.Violations;
import io.trellis.notification.domain.model.NotificationContent;
import org.springframework.stereotype.Component;

/**
 * Business rules of notification content, shared by create and update and by every entry point
 * (REST, GraphQL, a future consumer). Bean Validation on the request DTO only checks shape; these
 * rules are the contract.
 */
@Component
public class NotificationContentValidator implements CommandValidator<NotificationContent> {
  static final String FIELD_TITLE = "title";
  static final String FIELD_BODY = "body";
  static final String FIELD_SEVERITY = "severity";
  static final String FIELD_AUDIENCE = "audience";
  static final String REQUIRED = "REQUIRED";
  static final String TOO_LONG = "TOO_LONG";
  static final String CONTROL_CHARACTERS = "CONTROL_CHARACTERS";

  @Override
  public void validate(RequestContext ctx, NotificationContent content, Violations violations) {
    String title = content.title();
    violations.check(title != null && !title.isBlank(), FIELD_TITLE, REQUIRED, "title is required");
    if (title != null) {
      violations.check(
          title.length() <= NotificationContent.TITLE_MAX,
          FIELD_TITLE,
          TOO_LONG,
          "title must be at most " + NotificationContent.TITLE_MAX + " characters");
      // A title ends up in list rows, mail subjects and event payloads; line breaks there break
      // layouts.
      violations.check(
          title.chars().noneMatch(Character::isISOControl),
          FIELD_TITLE,
          CONTROL_CHARACTERS,
          "title must be a single line");
    }
    String body = content.body();
    violations.check(body != null && !body.isBlank(), FIELD_BODY, REQUIRED, "body is required");
    if (body != null) {
      violations.check(
          body.length() <= NotificationContent.BODY_MAX,
          FIELD_BODY,
          TOO_LONG,
          "body must be at most " + NotificationContent.BODY_MAX + " characters");
    }
    violations.check(content.severity() != null, FIELD_SEVERITY, REQUIRED, "severity is required");
    violations.check(content.audience() != null, FIELD_AUDIENCE, REQUIRED, "audience is required");
  }
}
