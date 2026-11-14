package io.trellis.platform.security;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Resource-server and policy settings.
 *
 * @param issuerUri OIDC issuer; keys are discovered from it lazily (first request), so startup never blocks on
 *     the identity provider
 * @param jwkSetUri explicit JWKS URL; overrides discovery when set
 * @param audiences accepted {@code aud} values; empty accepts any audience from the issuer
 * @param rolesClaim dotted path of the roles array in the token ({@code realm_access.roles} for Keycloak)
 * @param permissionsClaim claim that carries permissions directly (service tokens)
 * @param serviceClaim claim whose presence marks a service (client-credentials) token
 * @param rolePermissions role name to permissions it grants; the default {@code PermissionResolver} reads it
 * @param publicPaths extra unauthenticated path patterns (prefer {@link PublicEndpoint})
 * @param cors CORS policy; empty origins disables CORS
 * @param headers response security headers
 */
@ConfigurationProperties(prefix = "trellis.security")
public record SecurityProperties(
        String issuerUri,
        String jwkSetUri,
        @DefaultValue List<String> audiences,
        @DefaultValue("realm_access.roles") String rolesClaim,
        @DefaultValue("permissions") String permissionsClaim,
        @DefaultValue("client_id") String serviceClaim,
        @DefaultValue Map<String, List<String>> rolePermissions,
        @DefaultValue("/error") List<String> publicPaths,
        @DefaultValue Cors cors,
        @DefaultValue Headers headers) {

    public boolean hasKeySource() {
        return (issuerUri != null && !issuerUri.isBlank()) || (jwkSetUri != null && !jwkSetUri.isBlank());
    }

    /**
     * @param allowedOrigins exact origins (no wildcards with credentials)
     * @param allowedMethods allowed HTTP methods
     * @param allowedHeaders allowed request headers
     * @param maxAge preflight cache duration
     */
    public record Cors(
            @DefaultValue List<String> allowedOrigins,

            @DefaultValue({"GET", "POST", "PUT", "PATCH", "DELETE"})
            List<String> allowedMethods,

            @DefaultValue({"Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-Id", "traceparent"})
            List<String> allowedHeaders,

            @DefaultValue("PT1H") Duration maxAge) {}

    /**
     * @param contentSecurityPolicy CSP for API responses; the API never serves HTML, so everything is denied
     * @param hstsMaxAge Strict-Transport-Security max-age
     */
    public record Headers(
            @DefaultValue("default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'")
            String contentSecurityPolicy,

            @DefaultValue("P365D") Duration hstsMaxAge) {}
}
