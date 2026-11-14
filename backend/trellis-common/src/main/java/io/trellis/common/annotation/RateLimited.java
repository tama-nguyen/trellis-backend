package io.trellis.platform.aspect.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Token bucket per key: at most {@link #permits()} calls per {@link #per()}, refilled gradually. Over the limit
 * the method is not invoked and {@code RateLimitedException} (429 with Retry-After) is thrown.
 *
 * <p><b>Use</b> on expensive or abusable operations: sending mail, exports, login-like flows, public writes.
 *
 * <p><b>Do not use</b> as a global API limit (put that at the gateway/BFF) or for fairness between tenants. The
 * default store is per instance, so N instances allow N times the limit; set {@code
 * trellis.rate-limit.store=redis} where that matters.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimited {
    /** SpEL bucket key; empty means "per actor" ({@code actor type + id} from the RequestContext). */
    String key() default "";

    int permits();

    /** ISO-8601 refill period, e.g. {@code PT1M}. */
    String per();
}
