package io.trellis.common.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs the method only when {@link #flag()} is enabled. When disabled, calls {@link #fallback()} (a
 * method of the same bean with the same parameters and return type) or, without a fallback, throws
 * 404 {@code FEATURE_DISABLED} so a dark feature looks absent.
 *
 * <p><b>Use</b> for releasing a whole operation behind a flag that has an owner and a sunset date.
 *
 * <p><b>Do not use</b> for branching inside a method, or for per-user experiments (inject {@code
 * FeatureFlags} and branch explicitly). The fallback is invoked on the target object directly, so
 * its own annotations do not apply.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresFeature {
  /** Flag name as declared in {@code FeatureFlags}, kebab case. */
  String flag();

  /** Name of the fallback method; empty means "throw FEATURE_DISABLED". */
  String fallback() default "";
}
