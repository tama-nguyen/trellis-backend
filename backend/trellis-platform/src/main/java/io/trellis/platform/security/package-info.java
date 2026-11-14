/**
 * Role: the security every deployable needs, HTTP or not: actor resolution from the authenticated principal, the
 *     scanned permission catalogue, the deny-by-default {@code AuthorizationService}, the JWT decoder factory and
 *     the {@link io.trellis.platform.security.SensitiveProperties} classification of {@code trellis.*} keys.
 * May depend on: {@code io.trellis.common..}, Spring Boot autoconfigure, Spring Security core and
 *     oauth2-jose/resource-server, Micrometer.
 * Must not depend on: {@code io.trellis.platform.web..}, servlet or Spring MVC types - a consumer or upgrade job
 *     authorises use cases with these classes and ships without the HTTP edge. The filter chain, the
 *     {@code JwtDecoder} bean and {@code @PublicEndpoint} live in {@code io.trellis.platform.web.security}.
 */
package io.trellis.platform.security;
