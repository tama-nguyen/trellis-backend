package io.trellis.common.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.slf4j.event.Level;

/**
 * Logs one structured line per call: {@code operation}, {@code duration_ms}, {@code outcome} (ok or the exception
 * class). Arguments are not logged unless {@link #logArgs()} is true, and then parameters marked {@link Sensitive}
 * or named like a secret are masked and every value is truncated.
 *
 * <p><b>Use</b> on operations whose timing or frequency an operator wants to grep: imports, replays, rebuilds.
 *
 * <p><b>Do not use</b> on hot paths for metrics (use {@code @Observed}, which also traces), or to debug a single
 * issue (add a temporary log and remove it).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LogExecution {
    Level level() default Level.INFO;

    boolean logArgs() default false;
}
