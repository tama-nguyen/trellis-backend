package io.trellis.platform.aspect.support;

import io.trellis.common.context.Actor;
import io.trellis.common.context.AmbientRequestContext;
import io.trellis.common.context.RequestContext;
import io.trellis.common.id.UuidV7;
import java.lang.reflect.Method;
import java.time.Clock;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

/** Lookups shared by the aspects. */
public final class AspectContexts {
    private AspectContexts() {}

    /**
     * The operation's context: the method's explicit {@link RequestContext} argument if it has one (the norm for
     * application services), else the ambient one bound at the edge, else an anonymous context (which every
     * permission check denies).
     */
    public static RequestContext requestContext(ProceedingJoinPoint pjp, Clock clock) {
        for (Object arg : pjp.getArgs()) {
            if (arg instanceof RequestContext ctx) {
                return ctx;
            }
        }
        return AmbientRequestContext.current()
                .orElseGet(() -> RequestContext.builder()
                        .actor(Actor.anonymous())
                        .correlationId(UuidV7.next(clock).toString())
                        .requestTime(clock.instant())
                        .build());
    }

    public static Method method(ProceedingJoinPoint pjp) {
        return ((MethodSignature) pjp.getSignature()).getMethod();
    }

    /** Low-cardinality name of the advised method, {@code SimpleClass.method}, for keys, logs and metrics. */
    public static String operationName(ProceedingJoinPoint pjp) {
        Method method = method(pjp);
        return pjp.getTarget().getClass().getSimpleName().replaceAll("\\$\\$.*$", "") + "." + method.getName();
    }
}
