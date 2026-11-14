package io.trellis.platform.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a controller method (or every method of a controller) out of authentication. Everything else requires a
 * valid token: forgetting this annotation fails closed, forgetting a security rule never fails open.
 *
 * <p>Use for health-like or bootstrap endpoints (public config, login callbacks). Do not use to "temporarily"
 * open an endpoint; give the caller a token instead. Authorization ({@code @RequiresPermission}) still applies
 * to anything the method calls.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface PublicEndpoint {}
