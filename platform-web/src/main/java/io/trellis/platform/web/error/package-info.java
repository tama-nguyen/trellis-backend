/**
 * Exception to RFC 7807 translation. {@link io.trellis.platform.web.error.ProblemMapper}s (ordered) turn a
 * throwable into a {@link io.trellis.platform.web.error.MappedProblem}; {@link
 * io.trellis.platform.web.error.ProblemFactory} renders it with {@code code}, {@code correlationId} and {@code
 * traceId}. Other modules add mappers (security adds authentication/authorization) instead of new handlers.
 */
package io.trellis.platform.web.error;
