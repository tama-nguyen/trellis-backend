package io.trellis.common.validation;

import io.trellis.common.context.RequestContext;

/**
 * One business rule over a command. Implementations are stateless beans; they add violations
 * instead of throwing, so the pipeline can report every problem at once.
 *
 * @param <T> command type
 */
public interface CommandValidator<T> {

  /** Narrow the rule to some commands (Specification); most validators apply to all. */
  default boolean appliesTo(RequestContext ctx, T command) {
    return true;
  }

  /** Lower runs first. Cheap shape checks before rules that hit the database. */
  default int order() {
    return 0;
  }

  void validate(RequestContext ctx, T command, Violations violations);
}
