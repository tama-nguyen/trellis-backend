package io.trellis.common.arch;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exempts a class or method from an architecture rule. Read only by ArchUnit tests.
 *
 * <p>Every exemption must say why in {@link #reason()}, so a reviewer can judge it and a later
 * cleanup can find it. Name the rule being exempted in {@link #rule()}.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface ArchExempt {
  /** Identifier of the exempted rule, as named in the ArchUnit test. */
  String rule();

  /** Why the rule does not apply here. Required; an empty reason fails review. */
  String reason();
}
