/**
 * Role: the HTTP edge of security - the management and API filter chains, CORS and security headers, the
 *     {@code JwtDecoder} bean, {@code @PublicEndpoint} and its request matcher, and the mapping of Spring
 *     Security exceptions to RFC 7807 problems.
 * May depend on: {@code io.trellis.common..}, {@code io.trellis.platform.security..} (properties, actor mapping,
 *     JWT decoder factory), {@code io.trellis.platform.web.error..}, Spring Security web, Spring MVC.
 * Must not depend on: business modules, {@code io.trellis.app..}. Nothing outside the web module may depend on
 *     this package: it only exists where a servlet container does.
 */
package io.trellis.platform.web.security;
