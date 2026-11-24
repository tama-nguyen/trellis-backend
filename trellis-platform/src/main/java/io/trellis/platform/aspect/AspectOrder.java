package io.trellis.platform.aspect;

import org.springframework.core.Ordered;

/**
 * Advice order of every platform aspect and of {@code @Transactional}. Lower runs first
 * (outermost).
 *
 * <pre>
 * call ─▶ LogExecution ─▶ RequiresFeature ─▶ RateLimited ─▶ RequiresPermission ─▶ Idempotent
 *      ─▶ DistributedLock ─▶ [Transactional ─▶ Audited ─▶ @Observed ─▶ method]
 * </pre>
 *
 * <ol>
 *   <li><b>LogExecution</b> outermost: it times and logs every outcome, including rejections by
 *       inner aspects.
 *   <li><b>RequiresFeature</b>: a disabled feature should not spend rate-limit tokens or reveal
 *       authorization.
 *   <li><b>RateLimited</b> before authorization: cheap rejection of floods and of permission
 *       probing.
 *   <li><b>RequiresPermission</b> before Idempotent: an unauthorised caller must not get another
 *       caller's stored result by replaying its idempotency key.
 *   <li><b>Idempotent</b> before the lock and the transaction: a replay returns the stored result
 *       without locking or opening a transaction, and the result is recorded only after the
 *       transaction committed.
 *   <li><b>DistributedLock</b> outside the transaction: the lock is released after commit, so the
 *       next holder always sees committed data.
 *   <li><b>Transactional</b>.
 *   <li><b>Audited</b> inside the transaction: the audit row commits or rolls back with the change
 *       it describes.
 *   <li><b>@Observed</b> (Micrometer's {@code ObservedAspect}) has no order, i.e. lowest
 *       precedence: it measures the method body itself.
 * </ol>
 */
public final class AspectOrder {
  private static final int BASE = Ordered.HIGHEST_PRECEDENCE + 1000;
  private static final int STEP = 100;

  public static final int LOG_EXECUTION = BASE;
  public static final int FEATURE_TOGGLE = BASE + STEP;
  public static final int RATE_LIMITED = BASE + 2 * STEP;
  public static final int REQUIRES_PERMISSION = BASE + 3 * STEP;
  public static final int IDEMPOTENT = BASE + 4 * STEP;
  public static final int DISTRIBUTED_LOCK = BASE + 5 * STEP;

  /** Passed to {@code @EnableTransactionManagement(order = ...)}. */
  public static final int TRANSACTION = BASE + 6 * STEP;

  public static final int AUDITED = BASE + 7 * STEP;

  private AspectOrder() {}
}
