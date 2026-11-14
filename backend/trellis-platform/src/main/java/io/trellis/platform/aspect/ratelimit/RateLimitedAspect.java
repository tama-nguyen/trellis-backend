package io.trellis.platform.aspect.ratelimit;

import io.trellis.platform.aspect.AspectOrder;
import io.trellis.platform.aspect.support.AspectContexts;
import io.trellis.platform.aspect.support.SpelKeyEvaluator;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.RateLimitedException;
import java.time.Clock;
import java.time.Duration;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

/** Enforces {@link RateLimited}. Buckets are scoped per method, then per key. */
@Aspect
@Order(AspectOrder.RATE_LIMITED)
public class RateLimitedAspect {
    private final RateLimiterStore store;
    private final SpelKeyEvaluator spel;
    private final Clock clock;

    public RateLimitedAspect(RateLimiterStore store, SpelKeyEvaluator spel, Clock clock) {
        this.store = store;
        this.spel = spel;
        this.clock = clock;
    }

    @Around("@annotation(limited)")
    public Object limit(ProceedingJoinPoint pjp, RateLimited limited) throws Throwable {
        String key = limited.key().isEmpty()
                ? actorKey(AspectContexts.requestContext(pjp, clock))
                : spel.evaluate(limited.key(), AspectContexts.method(pjp), pjp.getArgs());
        String bucket = AspectContexts.operationName(pjp) + ":" + key;
        Duration wait = store.tryConsume(bucket, limited.permits(), Duration.parse(limited.per()));
        if (!wait.isZero()) {
            throw new RateLimitedException(wait);
        }
        return pjp.proceed();
    }

    private static String actorKey(RequestContext ctx) {
        return ctx.actor().type().name() + ":" + ctx.actor().id();
    }
}
