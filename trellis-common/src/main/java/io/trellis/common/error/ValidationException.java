package io.trellis.common.error;

import io.trellis.common.validation.Violation;
import java.util.List;

/** One or more business validation rules failed. Carries every violation, not only the first. */
public class ValidationException extends TrellisException {
  private final transient List<Violation> violations;

  public ValidationException(List<Violation> violations) {
    this(PlatformErrorCode.VALIDATION_FAILED, violations);
  }

  public ValidationException(ErrorCode errorCode, List<Violation> violations) {
    super(errorCode, violations.size() + " validation rule(s) failed");
    this.violations = List.copyOf(violations);
  }

  public List<Violation> violations() {
    return violations;
  }
}
