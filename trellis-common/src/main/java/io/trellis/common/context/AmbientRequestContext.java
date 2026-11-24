package io.trellis.common.context;

import java.util.Optional;

/**
 * Thread-bound {@link RequestContext} for framework edges only: the HTTP filter sets it, Kafka
 * consumers set it per record, executors copy it to worker threads, and aspects read it when a
 * method has no explicit {@code RequestContext} parameter.
 *
 * <p>Business code must not call {@link #current()}; take the context as a parameter instead. That
 * keeps the dependency visible and the code testable without thread-local setup.
 */
public final class AmbientRequestContext {
  private static final ThreadLocal<RequestContext> CURRENT = new ThreadLocal<>();

  private AmbientRequestContext() {}

  public static Optional<RequestContext> current() {
    return Optional.ofNullable(CURRENT.get());
  }

  /**
   * Binds {@code context} until the returned scope closes, then restores whatever was bound before
   * (so nested scopes, e.g. a consumer inside a test request, do not wipe the outer one).
   */
  public static Scope open(RequestContext context) {
    RequestContext previous = CURRENT.get();
    CURRENT.set(context);
    return () -> {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    };
  }

  /** Closeable without a checked exception, for try-with-resources. */
  @FunctionalInterface
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
