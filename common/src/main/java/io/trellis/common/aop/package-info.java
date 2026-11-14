/**
 * Role: the cross-cutting annotations of decision T8 ({@code @RequiresPermission}, {@code @Audited},
 *     {@code @Idempotent}, {@code @RateLimited}, {@code @DistributedLock}, {@code @LogExecution},
 *     {@code @RequiresFeature}, {@code @Sensitive}). Declarations only: a business module states its intent
 *     without pulling in the implementation (decision T19).
 * May depend on: the JDK and slf4j (the level of {@code @LogExecution}) only.
 * Must not depend on: anything in {@code io.trellis.platform..}, Spring, Jackson, Jakarta. The aspects that give
 *     these annotations meaning live in {@code io.trellis.platform.aspect}; the ordering is pinned in
 *     {@code io.trellis.platform.aspect.AspectOrder}.
 */
package io.trellis.common.aop;
