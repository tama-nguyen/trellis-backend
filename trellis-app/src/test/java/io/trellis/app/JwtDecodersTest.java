package io.trellis.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import io.trellis.platform.security.SecurityProperties;
import io.trellis.platform.security.jwt.JwtDecoders;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class JwtDecodersTest {
  private static final String ISSUER = "https://trusted.example.test";
  private static final String AUDIENCE = "trellis-api";
  private static final String SUBJECT = "external-subject";
  private static final Duration VALIDITY = Duration.ofMinutes(5);
  private RSAKey key;
  private HttpServer server;
  private SecurityProperties properties;

  @BeforeEach
  void serveTrustedJwks() throws Exception {
    key = new RSAKeyGenerator(2048).generate();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/keys",
        exchange -> {
          val response = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          try (val output = exchange.getResponseBody()) {
            output.write(response);
          }
        });
    server.start();

    properties = new SecurityProperties();
    ReflectionTestUtils.setField(properties, "issuerUri", ISSUER);
    ReflectionTestUtils.setField(
        properties, "jwkSetUri", "http://127.0.0.1:" + server.getAddress().getPort() + "/keys");
    ReflectionTestUtils.setField(properties, "audiences", List.of(AUDIENCE));
  }

  @AfterEach
  void stopJwksServer() {
    server.stop(0);
  }

  @Test
  void shouldAcceptJwtFromTrustedIssuer() {
    // Given
    val token = token(ISSUER, AUDIENCE, key, Instant.now().plus(VALIDITY));
    val decoder = JwtDecoders.create(properties, false);

    // When
    val decoded = decoder.decode(token);

    // Then
    assertEquals(ISSUER, decoded.getIssuer().toString());
    assertEquals(SUBJECT, decoded.getSubject());
    assertEquals(List.of(AUDIENCE), decoded.getAudience());
  }

  @Test
  void shouldRejectJwtFromAnotherIssuerEvenWithTheSameKey() {
    // Given
    val token = token("https://other.example.test", AUDIENCE, key, Instant.now().plus(VALIDITY));
    val decoder = JwtDecoders.create(properties, false);

    // When & Then
    assertThrows(BadJwtException.class, () -> decoder.decode(token));
  }

  @Test
  void shouldRejectJwtWithoutIssuer() {
    // Given
    val token = token(null, AUDIENCE, key, Instant.now().plus(VALIDITY));
    val decoder = JwtDecoders.create(properties, false);

    // When & Then
    assertThrows(BadJwtException.class, () -> decoder.decode(token));
  }

  @Test
  void shouldRejectJwtWithAnotherAudience() {
    // Given
    val token = token(ISSUER, "other-api", key, Instant.now().plus(VALIDITY));
    val decoder = JwtDecoders.create(properties, false);

    // When & Then
    assertThrows(BadJwtException.class, () -> decoder.decode(token));
  }

  @Test
  void shouldRejectExpiredJwt() {
    // Given
    val token = token(ISSUER, AUDIENCE, key, Instant.now().minus(VALIDITY));
    val decoder = JwtDecoders.create(properties, false);

    // When & Then
    assertThrows(BadJwtException.class, () -> decoder.decode(token));
  }

  @Test
  void shouldRejectJwtSignedByAnotherKey() throws Exception {
    // Given
    val otherKey = new RSAKeyGenerator(2048).generate();
    val token = token(ISSUER, AUDIENCE, otherKey, Instant.now().plus(VALIDITY));
    val decoder = JwtDecoders.create(properties, false);

    // When & Then
    assertThrows(BadJwtException.class, () -> decoder.decode(token));
  }

  @Test
  void shouldRejectJwtBeforeItsNotBeforeInstant() {
    // Given
    val now = Instant.now();
    val claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(SUBJECT)
            .issuedAt(now)
            .notBefore(now.plus(VALIDITY))
            .expiresAt(now.plus(VALIDITY.multipliedBy(2)))
            .audience(List.of(AUDIENCE))
            .build();
    val token = sign(claims, key);
    val decoder = JwtDecoders.create(properties, false);

    // When & Then
    assertThrows(BadJwtException.class, () -> decoder.decode(token));
  }

  @Test
  void shouldRejectJwksConfigurationWithoutIssuer() {
    // Given
    ReflectionTestUtils.setField(properties, "issuerUri", "");

    // When & Then
    assertThrows(IllegalStateException.class, () -> JwtDecoders.create(properties, true));
  }

  @Test
  void shouldRejectProductionConfigurationWithoutAudience() {
    // Given
    ReflectionTestUtils.setField(properties, "audiences", List.of());

    // When & Then
    assertThrows(IllegalStateException.class, () -> JwtDecoders.create(properties, false));
  }

  @Test
  void shouldRejectJwtInNativeOnlyModeWithoutAKeySource() {
    // Given
    ReflectionTestUtils.setField(properties, "issuerUri", "");
    ReflectionTestUtils.setField(properties, "jwkSetUri", "");
    val decoder = JwtDecoders.create(properties, true);

    // When & Then
    assertThrows(BadJwtException.class, () -> decoder.decode("untrusted"));
  }

  @Test
  void shouldReportJwksOutageAsInfrastructureFailure() {
    // Given
    val token = token(ISSUER, AUDIENCE, key, Instant.now().plus(VALIDITY));
    server.stop(0);

    // When & Then
    assertThrows(AuthenticationServiceException.class, () -> decodeWithConfiguredSource(token));
  }

  private Jwt decodeWithConfiguredSource(final String token) {
    return JwtDecoders.create(properties, false).decode(token);
  }

  private String token(
      final String issuer, final String audience, final RSAKey signingKey, final Instant expiry) {
    val claims =
        JwtClaimsSet.builder()
            .subject(SUBJECT)
            .issuedAt(expiry.minus(VALIDITY.multipliedBy(2)))
            .expiresAt(expiry)
            .audience(List.of(audience));
    if (issuer != null) {
      claims.issuer(issuer);
    }
    return sign(claims.build(), signingKey);
  }

  private String sign(final JwtClaimsSet claims, final RSAKey signingKey) {
    val encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    val header = JwsHeader.with(SignatureAlgorithm.RS256).build();
    val parameters = JwtEncoderParameters.from(header, claims);
    return encoder.encode(parameters).getTokenValue();
  }
}
