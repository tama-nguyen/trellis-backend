package io.trellis.app.support;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.val;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

public final class TestJwtIssuer {
  private TestJwtIssuer() {}

  public static final String ISSUER = "https://issuer.example.test";
  private static final RSAKey KEY = key();

  public static JwtDecoder decoder() throws Exception {
    val decoder = NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
    return decoder;
  }

  public static String jwt(final String subject, final String role) {
    val claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(subject)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("email", "external@example.test");
    if (role != null) {
      claims.claim("realm_access", Map.of("roles", List.of(role)));
    }
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY)))
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()))
        .getTokenValue();
  }

  private static RSAKey key() {
    try {
      return new RSAKeyGenerator(2048).generate();
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }
}
