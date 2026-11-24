package io.trellis.common.aop;

import java.lang.annotation.*;

/** Declares an operation quota enforced before its transaction. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimited {
  String policy();

  String key() default "";

  /** SKIP_VOID completes a void operation without invoking it when its quota is exhausted. */
  OnExceeded onExceeded() default OnExceeded.REJECT;

  enum OnExceeded {
    REJECT,
    SKIP_VOID
  }
}
