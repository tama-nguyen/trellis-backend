package io.trellis.common.validation;

import io.trellis.common.context.RequestContext;
import java.util.Comparator;
import java.util.List;

/**
 * Runs every applicable validator in order and throws one {@code ValidationException} carrying all
 * violations ("run all, collect", not fail-fast).
 */
public final class ValidationPipeline<T> {
  private final List<CommandValidator<? super T>> validators;

  public ValidationPipeline(List<? extends CommandValidator<? super T>> validators) {
    this.validators =
        validators.stream()
            .<CommandValidator<? super T>>map(v -> v)
            .sorted(Comparator.comparingInt(CommandValidator::order))
            .toList();
  }

  public void validate(RequestContext ctx, T command) {
    Violations violations = new Violations();
    for (CommandValidator<? super T> validator : validators) {
      if (validator.appliesTo(ctx, command)) {
        validator.validate(ctx, command, violations);
      }
    }
    violations.throwIfAny();
  }
}
