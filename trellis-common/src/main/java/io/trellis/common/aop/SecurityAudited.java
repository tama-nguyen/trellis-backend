package io.trellis.common.aop;

import io.trellis.common.security.SecurityAction;
import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SecurityAudited {
  SecurityAction value();

  boolean recordSuccess() default true;
}
