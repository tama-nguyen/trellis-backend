package io.trellis.platform.aspect.authz;

import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.security.AuthorizationService;
import io.trellis.common.security.PermissionRequirement;
import io.trellis.common.security.ResourceScope;
import io.trellis.platform.aspect.AspectOrder;
import io.trellis.platform.aspect.support.AspectContexts;
import java.time.Clock;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

/** Enforces {@link RequiresPermission}. */
@Aspect
@Order(AspectOrder.REQUIRES_PERMISSION)
public class RequiresPermissionAspect {
  private final AuthorizationService authorization;
  private final Clock clock;

  public RequiresPermissionAspect(AuthorizationService authorization, Clock clock) {
    this.authorization = authorization;
    this.clock = clock;
  }

  @Around("@annotation(requires)")
  public Object check(ProceedingJoinPoint pjp, RequiresPermission requires) throws Throwable {
    authorization.require(
        AspectContexts.requestContext(pjp, clock),
        PermissionRequirement.of(requires.anyOf(), requires.allOf()),
        ResourceScope.global());
    return pjp.proceed();
  }
}
