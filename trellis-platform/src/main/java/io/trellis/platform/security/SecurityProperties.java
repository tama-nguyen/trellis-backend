package io.trellis.platform.security;

import java.time.Duration;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;

/**
 * Resource-server and policy settings.
 *
 * <p>issuerUri: OIDC issuer; keys are discovered from it lazily (first request), so startup never
 * blocks on the identity provider jwkSetUri: explicit JWKS URL; overrides discovery when set
 * audiences: accepted {@code aud} values; required outside dev/test rolesClaim: dotted path of the
 * roles array in the token ({@code realm_access.roles} for Keycloak) permissionsClaim: claim that
 * carries permissions directly (service tokens) serviceClaim: claim whose presence marks a service
 * (client-credentials) token publicPaths: extra unauthenticated path patterns (prefer {@link
 * PublicEndpoint}) cors: CORS policy; empty origins disables CORS headers: response security
 * headers
 */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
public final class SecurityProperties {
  @Value("${trellis.security.issuer-uri:}")
  private String issuerUri;

  @Value("${trellis.security.jwk-set-uri:}")
  private String jwkSetUri;

  @Value("${trellis.security.audiences:}")
  private List<String> audiences;

  @Value("${trellis.security.roles-claim:realm_access.roles}")
  private String rolesClaim;

  @Value("${trellis.security.permissions-claim:permissions}")
  private String permissionsClaim;

  @Value("${trellis.security.service-claim:client_id}")
  private String serviceClaim;

  @Value("${trellis.security.public-paths:/error}")
  private List<String> publicPaths;

  @Value("${trellis.security.cors.allowed-origins:}")
  private List<String> corsAllowedOrigins;

  @Value("${trellis.security.cors.allowed-methods:GET,POST,PUT,PATCH,DELETE}")
  private List<String> corsAllowedMethods;

  @Value(
      "${trellis.security.cors.allowed-headers:Authorization,Content-Type,Idempotency-Key,X-Correlation-Id,traceparent}")
  private List<String> corsAllowedHeaders;

  @Value("${trellis.security.cors.max-age:PT1H}")
  private Duration corsMaxAge;

  @Value(
      "${trellis.security.headers.content-security-policy:default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'}")
  private String headersContentSecurityPolicy;

  @Value("${trellis.security.headers.hsts-max-age:P365D}")
  private Duration headersHstsMaxAge;

  public SecurityProperties(
      String issuerUri,
      String jwkSetUri,
      List<String> audiences,
      String rolesClaim,
      String permissionsClaim,
      String serviceClaim,
      List<String> publicPaths,
      Cors cors,
      Headers headers) {
    this.issuerUri = issuerUri;
    this.jwkSetUri = jwkSetUri;
    this.audiences = audiences;
    this.rolesClaim = rolesClaim;
    this.permissionsClaim = permissionsClaim;
    this.serviceClaim = serviceClaim;
    this.publicPaths = publicPaths;
    this.corsAllowedOrigins = cors.allowedOrigins();
    this.corsAllowedMethods = cors.allowedMethods();
    this.corsAllowedHeaders = cors.allowedHeaders();
    this.corsMaxAge = cors.maxAge();
    this.headersContentSecurityPolicy = headers.contentSecurityPolicy();
    this.headersHstsMaxAge = headers.hstsMaxAge();
  }

  public Cors cors() {
    return new Cors(corsAllowedOrigins, corsAllowedMethods, corsAllowedHeaders, corsMaxAge);
  }

  public Headers headers() {
    return new Headers(headersContentSecurityPolicy, headersHstsMaxAge);
  }

  public boolean hasKeySource() {
    return (issuerUri != null && !issuerUri.isBlank())
        || (jwkSetUri != null && !jwkSetUri.isBlank());
  }

  public boolean hasAudience() {
    return !audiences.isEmpty() && audiences.stream().anyMatch(audience -> !audience.isBlank());
  }

  /**
   * allowedOrigins: exact origins (no wildcards with credentials) allowedMethods: allowed HTTP
   * methods allowedHeaders: allowed request headers maxAge: preflight cache duration
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class Cors {
    private final List<String> allowedOrigins;
    private final List<String> allowedMethods;
    private final List<String> allowedHeaders;
    private final Duration maxAge;
  }

  /**
   * contentSecurityPolicy: CSP for API responses; the API never serves HTML, so everything is
   * denied hstsMaxAge: Strict-Transport-Security max-age
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class Headers {
    private final String contentSecurityPolicy;
    private final Duration hstsMaxAge;
  }
}
