package io.trellis.platform.aspect.logging;

import io.trellis.platform.aspect.AspectOrder;
import io.trellis.platform.aspect.support.AspectContexts;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

/** Enforces {@link LogExecution}; outermost aspect (see AspectOrder). */
@Aspect
@Order(AspectOrder.LOG_EXECUTION)
public class LogExecutionAspect {
    private static final Logger log = LoggerFactory.getLogger("trellis.execution");
    private static final Pattern SECRET_NAME =
            Pattern.compile("(?i).*(password|secret|token|credential|apikey|api_key|authorization).*");
    private static final String MASK = "***";
    private static final int MAX_VALUE_LENGTH = 200;
    private static final String OUTCOME_OK = "ok";

    @Around("@annotation(logExecution)")
    public Object logExecution(ProceedingJoinPoint pjp, LogExecution logExecution) throws Throwable {
        long start = System.nanoTime();
        String outcome = OUTCOME_OK;
        try {
            return pjp.proceed();
        } catch (Throwable e) {
            outcome = e.getClass().getSimpleName();
            throw e;
        } finally {
            var event = log.atLevel(logExecution.level())
                    .addKeyValue("operation", AspectContexts.operationName(pjp))
                    .addKeyValue("duration_ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
                    .addKeyValue("outcome", outcome);
            if (logExecution.logArgs()) {
                event = event.addKeyValue("args", redactedArgs(AspectContexts.method(pjp), pjp.getArgs()));
            }
            event.log("Operation executed");
        }
    }

    static Map<String, String> redactedArgs(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            Parameter p = parameters[i];
            boolean secret = p.isAnnotationPresent(Sensitive.class)
                    || SECRET_NAME.matcher(p.getName()).matches();
            out.put(p.getName(), secret ? MASK : truncate(String.valueOf(args[i])));
        }
        return out;
    }

    private static String truncate(String value) {
        return value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH) + "…";
    }
}
