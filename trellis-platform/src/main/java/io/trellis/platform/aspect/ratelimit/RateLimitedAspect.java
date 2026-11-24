package io.trellis.platform.aspect.ratelimit;

import io.trellis.common.aop.RateLimited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.RateLimitedException;
import io.trellis.platform.aspect.AspectOrder;
import io.trellis.platform.aspect.support.AspectContexts;
import io.trellis.platform.aspect.support.SpelKeyEvaluator;
import java.time.Clock;
import java.time.Duration;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

/** Enforces {@link RateLimited}. Buckets are scoped by stable policy ID and canonical subject. */
@Aspect
@Order(AspectOrder.RATE_LIMITED)
public class RateLimitedAspect {
  private final RateLimitEngine store;
  private final SpelKeyEvaluator spel;
  private final Clock clock;

  public RateLimitedAspect(RateLimitEngine store, SpelKeyEvaluator spel, Clock clock) {
    this.store = store;
    this.spel = spel;
    this.clock = clock;
  }

  @Around("@annotation(limited)")
  public Object limit(ProceedingJoinPoint pjp, RateLimited limited) throws Throwable {
    if (limited.onExceeded() == RateLimited.OnExceeded.SKIP_VOID
        && AspectContexts.method(pjp).getReturnType() != void.class) {
      throw new IllegalStateException("SKIP_VOID rate limiting requires a void method");
    }
    String key =
        limited.key().isEmpty()
            ? actorKey(AspectContexts.requestContext(pjp, clock))
            : spel.evaluate(limited.key(), AspectContexts.method(pjp), pjp.getArgs());

    Duration wait = store.consume(limited.policy(), key);
    if (!wait.isZero()) {
      if (limited.onExceeded() == RateLimited.OnExceeded.SKIP_VOID) {
        return null;
      }
      throw new RateLimitedException(wait);
    }
    return pjp.proceed();
  }

  private static String actorKey(RequestContext ctx) {
    return ctx.actor().type().name() + ":" + ctx.actor().id();
  }
}
