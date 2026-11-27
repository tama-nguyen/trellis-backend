package io.trellis.common.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs the method while holding a cluster-wide lock on {@link #key()} (a Postgres session advisory
 * lock). Waits up to {@link #waitFor()}; if the lock is still held, throws 409 {@code
 * LOCK_NOT_ACQUIRED} without invoking the method. The lock is released after the method (and its
 * transaction) finished, or when the connection dies.
 *
 * <p><b>Use</b> to serialise work that row locks cannot cover: a job that must not run twice, a
 * computation over many rows of one aggregate, calls to an external system that cannot take
 * concurrent requests.
 *
 * <p><b>Do not use</b> to protect a single-row update (use the optimistic {@code version} column),
 * and keep the critical section short: the lock pins one pooled connection for its whole duration.
 * {@link #leaseFor()} is a budget, not a timeout: Postgres cannot expire a session lock, so
 * overruns are logged and counted, and the lock is still released only when the method ends.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DistributedLock {
  /** SpEL lock name, e.g. {@code 'notification:' + #notificationId}. Scoped per method. */
  String key();

  /** ISO-8601 maximum wait for the lock; {@code PT0S} means try once. */
  String waitFor() default "PT0S";

  /** ISO-8601 expected maximum hold time; exceeding it logs a warning and increments a metric. */
  String leaseFor() default "PT30S";
}
