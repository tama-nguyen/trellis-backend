/**
 * Cross-cutting concerns as annotations on application-service methods (decision T8): {@code @RequiresPermission},
 * import io.trellis.common.annotation.Audited;
 * import io.trellis.common.annotation.DistributedLock;
 * import io.trellis.common.annotation.Idempotent;
 * import io.trellis.common.annotation.LogExecution;
 * import io.trellis.common.annotation.RateLimited;
 * import io.trellis.common.annotation.RequiresFeature;
 * import io.trellis.common.annotation.RequiresPermission;
 *
 * {@code @Audited}, {@code @Idempotent}, {@code @RateLimited}, {@code @DistributedLock}, {@code @LogExecution},
 * {@code @RequiresFeature}. Metrics and tracing use Micrometer's {@code @Observed}.
 *
 * <p>Rules (ArchUnit enforces the first three):
 *
 * <ul>
 *   <li>Annotations go only on public methods of Spring beans in an {@code ..application..} package.
 *   <li>A bean never calls its own annotated method through {@code this}: the call skips the proxy, so the
 *       aspect silently does not run. Move the method to another bean instead.
 *   <li>Aspect order relative to {@code @Transactional} is fixed in {@link io.trellis.platform.aspect.AspectOrder}.
 *   <li>Business rules that depend on loaded data (e.g. "only the owner may edit") stay explicit calls to
 *       {@code AuthorizationService}; an annotation cannot see the row.
 * </ul>
 */
package io.trellis.platform.aspect;
