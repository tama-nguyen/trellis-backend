package io.trellis.platform.aspect.feature;

import io.trellis.common.annotation.RequiresFeature;
import io.trellis.common.error.NotFoundException;
import io.trellis.common.error.PlatformErrorCode;
import io.trellis.platform.aspect.AspectOrder;
import io.trellis.platform.aspect.support.AspectContexts;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.util.ReflectionUtils;

/** Enforces {@link RequiresFeature}. */
@Aspect
@Order(AspectOrder.FEATURE_TOGGLE)
public class RequiresFeatureAspect {
    private final FeatureFlagSource flags;

    public RequiresFeatureAspect(FeatureFlagSource flags) {
        this.flags = flags;
    }

    @Around("@annotation(toggle)")
    public Object toggle(ProceedingJoinPoint pjp, RequiresFeature toggle) throws Throwable {
        if (flags.isEnabled(toggle.flag())) {
            return pjp.proceed();
        }
        if (toggle.fallback().isEmpty()) {
            throw new NotFoundException(PlatformErrorCode.FEATURE_DISABLED, "This feature is not available");
        }
        Method method = AspectContexts.method(pjp);
        Method fallback =
                ReflectionUtils.findMethod(pjp.getTarget().getClass(), toggle.fallback(), method.getParameterTypes());
        if (fallback == null) {
            throw new IllegalStateException("Fallback '" + toggle.fallback() + "' not found for " + method);
        }
        ReflectionUtils.makeAccessible(fallback);
        try {
            return fallback.invoke(pjp.getTarget(), pjp.getArgs());
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
