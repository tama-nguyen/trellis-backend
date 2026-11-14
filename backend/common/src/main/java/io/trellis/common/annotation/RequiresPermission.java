package io.trellis.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The caller must hold every permission in {@link #allOf()} and at least one in {@link #anyOf()}; otherwise the
 * method is not invoked and {@code ForbiddenException} (403) is thrown. Checked by {@code AuthorizationService}
 * against the method's {@code RequestContext} argument.
 *
 * <p><b>Use</b> for operation-level rules that depend only on who calls: "may publish notices".
 *
 * <p><b>Do not use</b> for data-dependent rules ("only the author may edit this notice"): load the data and call
 * {@code AuthorizationService.require} explicitly. Do not put it on controllers; GraphQL and consumers call the
 * service directly and would bypass it.
 *
 * <p>Values must be constants from a {@code @PermissionCatalog} class; unknown names fail startup.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresPermission {
    String[] anyOf() default {};

    String[] allOf() default {};
}
