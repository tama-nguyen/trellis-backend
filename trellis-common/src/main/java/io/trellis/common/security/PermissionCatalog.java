package io.trellis.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a final class whose {@code public static final String} fields are the permissions of one
 * module.
 *
 * <p>Convention: one catalog per module ({@code NoticePermissions}), constants named after the
 * action, values {@code <module>:<action>}. The platform scans catalogs at startup to build the
 * permission registry and fails when an {@code @RequiresPermission} names a permission no catalog
 * declares, so typos never silently deny.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PermissionCatalog {}
