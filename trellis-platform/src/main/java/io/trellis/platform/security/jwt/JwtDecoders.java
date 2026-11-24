package io.trellis.platform.security.jwt;

import io.trellis.platform.security.SecurityProperties;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;

/**
 * Builds the {@link JwtDecoder}. Keys come from {@code jwk-set-uri} or are discovered from {@code
 * issuer-uri}; discovery is deferred to the first request so a slow identity provider never blocks
 * startup. Without either setting the application refuses to start, except under the {@code
 * dev}/{@code test} profiles where every token is rejected instead (public endpoints still work).
 */
public final class JwtDecoders {
  private static final Logger log = LoggerFactory.getLogger(JwtDecoders.class);

  private JwtDecoders() {}

  public static JwtDecoder create(SecurityProperties properties, boolean lenientProfile) {
    if (!properties.hasKeySource()) {
      if (!lenientProfile) {
        throw new IllegalStateException(
            "trellis.security.issuer-uri or trellis.security.jwk-set-uri must be set outside the dev and test profiles");
      }
      log.warn("No JWT issuer configured: every bearer token will be rejected (dev/test only)");
      return token -> {
        throw new BadJwtException("No JWT issuer is configured");
      };
    }
    if (properties.issuerUri() == null || properties.issuerUri().isBlank()) {
      throw new IllegalStateException(
          "OIDC requires trellis.security.issuer-uri, including with an explicit JWKS URI");
    }
    if (!properties.hasAudience()) {
      if (!lenientProfile) {
        throw new IllegalStateException(
            "trellis.security.audiences must contain at least one audience outside the dev and test profiles");
      }
      log.warn("No JWT audience configured: audience validation is disabled (dev/test only)");
    }
    return new IdpFailureTranslatingDecoder(new SupplierJwtDecoder(() -> build(properties)));
  }

  /**
   * Key discovery or JWKS fetch failures are the identity provider's fault, not the token's. Spring
   * reports them as plain runtime exceptions that would surface as 500; translate them to {@link
   * AuthenticationServiceException}, which the platform maps to 503 with Retry-After.
   */
  private record IdpFailureTranslatingDecoder(JwtDecoder delegate) implements JwtDecoder {
    @Override
    public Jwt decode(String token) {
      try {
        return delegate.decode(token);
      } catch (BadJwtException e) {
        throw e;
      } catch (RuntimeException e) {
        throw new AuthenticationServiceException("Identity provider unavailable", e);
      }
    }
  }

  private static JwtDecoder build(SecurityProperties properties) {
    NimbusJwtDecoder decoder =
        properties.jwkSetUri() != null && !properties.jwkSetUri().isBlank()
            ? NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build()
            : NimbusJwtDecoder.withIssuerLocation(properties.issuerUri()).build();
    List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
    validators.add(JwtValidators.createDefaultWithIssuer(properties.issuerUri()));
    if (!properties.audiences().isEmpty()) {
      validators.add(
          new JwtClaimValidator<List<String>>(
              JwtClaimNames.AUD,
              aud -> aud != null && aud.stream().anyMatch(properties.audiences()::contains)));
    }
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
    return decoder;
  }
}
