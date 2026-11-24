package io.trellis.platform.web.error;

import java.util.Optional;

/**
 * Translates throwables a module understands. Implementations are beans; they are consulted in
 * {@code @Order} order and the first non-empty answer wins. Unmapped throwables become {@code
 * INTERNAL_ERROR}.
 */
@FunctionalInterface
public interface ProblemMapper {
  Optional<MappedProblem> map(Throwable error);
}
