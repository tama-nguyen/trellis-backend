package io.trellis.common.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Records an {@code audit_event} row when the method returns normally, inside the method's transaction: the audit
 * row commits exactly when the change commits. Requires an active transaction ({@code @Transactional} on the
 * same method); without one startup is fine but the call fails, on purpose.
 *
 * <p><b>Use</b> for state changes an operator or auditor must be able to reconstruct: who published, deleted,
 * granted, replayed what and when.
 *
 * <p><b>Do not use</b> for reads, or for failures (a rolled-back transaction keeps nothing; failed attempts belong
 * in logs and metrics). Never put secrets or personal data in the id expression.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {
    /** Verb in snake case, e.g. {@code notice.published}. Prefer a constant. */
    String action();

    /** PascalCase aggregate, e.g. {@code Notice}. */
    String aggregateType();

    /** SpEL over the parameters and {@code #result}, e.g. {@code #result.id()}. Empty means no single aggregate. */
    String aggregateId() default "";
}
