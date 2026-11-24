package io.trellis.common.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes a write safe to retry: the first call with a key runs and its result is stored (as JSON)
 * for {@link #ttl()}; later calls with the same key get the stored result without running the
 * method. A call while the first is still running gets 409 {@code IDEMPOTENCY_IN_PROGRESS}. If the
 * method throws, the key is released so the client can retry.
 *
 * <p><b>Use</b> on create/charge/send style commands reachable from clients that retry (mobile,
 * BFF, workers), with the key taken from the {@code Idempotency-Key} header or a client-generated
 * command id.
 *
 * <p><b>Do not use</b> on naturally idempotent operations (PUT by id, upserts) or reads. The return
 * type must round-trip through JSON (records, DTOs); a missing key (null or blank) runs the method
 * without protection. Keys are scoped per method, so two operations may reuse the same client key.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {
  /** SpEL over the parameters, e.g. {@code #idempotencyKey} or {@code #cmd.requestId()}. */
  String key();

  /** ISO-8601 duration the stored result is replayed for. */
  String ttl() default "PT24H";
}
