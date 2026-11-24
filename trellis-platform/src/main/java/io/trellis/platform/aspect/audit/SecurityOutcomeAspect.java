package io.trellis.platform.aspect.audit;

import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.error.TrellisException;
import io.trellis.platform.aspect.AspectOrder;
import io.trellis.platform.aspect.support.AspectContexts;
import io.trellis.platform.security.audit.SecurityEventRecorder;
import java.time.Clock;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.annotation.Order;

@Aspect
@Order(AspectOrder.REQUIRES_PERMISSION - 1)
public class SecurityOutcomeAspect {
  private final SecurityEventRecorder events;
  private final Clock clock;

  public SecurityOutcomeAspect(SecurityEventRecorder events, Clock clock) {
    this.events = events;
    this.clock = clock;
  }

  @Around("@annotation(audited)")
  public Object observe(ProceedingJoinPoint pjp, SecurityAudited audited) throws Throwable {
    var ctx = AspectContexts.requestContext(pjp, clock);
    try {
      Object result = pjp.proceed();
      if (audited.recordSuccess())
        events.record(ctx, audited.value(), SecurityEventRecorder.Outcome.SUCCESS, null);
      return result;
    } catch (TrellisException rejected) {
      events.record(
          ctx,
          audited.value(),
          rejected.errorCode().category().isServerError()
              ? SecurityEventRecorder.Outcome.ERROR
              : SecurityEventRecorder.Outcome.REJECTED,
          rejected.errorCode().code());
      throw rejected;
    } catch (Throwable error) {
      events.record(ctx, audited.value(), SecurityEventRecorder.Outcome.ERROR, null);
      throw error;
    }
  }
}
