package io.trellis.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.trellis.common.context.ActorType;
import io.trellis.platform.security.jwt.OidcPrincipalIdEncoder;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OidcPrincipalIdEncoderTest {
  private static final int MAX_STORED_ID_LENGTH = 200;

  @ParameterizedTest
  @MethodSource("storedIdFormats")
  void shouldRecognizeOnlyCanonicalTypedStoredIds(
      final String principalId, final boolean userId, final boolean serviceId) {
    // When
    val recognizedUser = OidcPrincipalIdEncoder.isUserId(principalId);
    val recognizedService = OidcPrincipalIdEncoder.isServiceId(principalId);

    // Then
    assertEquals(userId, recognizedUser);
    assertEquals(serviceId, recognizedService);
  }

  @ParameterizedTest(name = "Persisted {0} identifier for {1}")
  @MethodSource("identityVectors")
  void preservesExistingPrincipalIdVectors(
      final ActorType type, final String issuer, final String subject, final String expected) {
    // When
    val encoded = encode(type, issuer, subject);

    // Then
    assertEquals(expected, encoded);
  }

  @ParameterizedTest
  @MethodSource("invalidIdentities")
  void rejectsMissingIssuerAndSubject(
      final ActorType type,
      final String issuer,
      final String subject,
      final Class<? extends RuntimeException> expectedFailure) {
    // When & Then
    assertThrows(expectedFailure, () -> encode(type, issuer, subject));
  }

  @Test
  void issuerAndActorTypePartitionExternalPrincipalKeys() {
    // Given
    val nativeId = UUID.randomUUID().toString();
    val firstIssuer = "https://one.example.test";
    val secondIssuer = "https://two.example.test";

    // When
    val userId = OidcPrincipalIdEncoder.userId(firstIssuer, nativeId);
    val anotherIssuerId = OidcPrincipalIdEncoder.userId(secondIssuer, nativeId);
    val serviceId = OidcPrincipalIdEncoder.serviceId(firstIssuer, nativeId);
    val repeated = OidcPrincipalIdEncoder.userId(firstIssuer, nativeId);

    // Then
    assertNotEquals(userId, nativeId);
    assertNotEquals(userId, anotherIssuerId);
    assertNotEquals(userId, serviceId);
    assertEquals(userId, repeated);
    assertTrue(userId.length() < MAX_STORED_ID_LENGTH);
  }

  @Test
  void concatenatedIssuerAndSubjectCannotCollideByBoundary() {
    // When
    val first = OidcPrincipalIdEncoder.userId("https://example.test/a", "bc");
    val second = OidcPrincipalIdEncoder.userId("https://example.test/ab", "c");

    // Then
    assertNotEquals(first, second);
  }

  private static Stream<Arguments> identityVectors() {
    return Stream.of(
        Arguments.of(
            ActorType.USER,
            "https://issuer.example.test",
            "subject-001",
            "oidc-user:d28870237e200035cce1bb7ec6432fb543ce2bdef170e35ea9c7e7335a4918fe"),
        Arguments.of(
            ActorType.SERVICE,
            "https://issuer.example.test",
            "client-001",
            "oidc-service:69d40358cd48ce3cf394ce0a6bc338c5bb39847205747ea5ddd5e3622c42adb1"),
        Arguments.of(
            ActorType.USER,
            "https://issuer.example.test/😀",
            "subject-001",
            "oidc-user:4875aacc6861b8099a808a46a60766c94523db5885b592a532c9f1105dc6e807"));
  }

  private static Stream<Arguments> storedIdFormats() {
    val user = OidcPrincipalIdEncoder.userId("https://issuer.example.test", "format-user");
    val service = OidcPrincipalIdEncoder.serviceId("https://issuer.example.test", "format-client");
    return Stream.of(
        Arguments.of(user, true, false),
        Arguments.of(service, false, true),
        Arguments.of(null, false, false),
        Arguments.of(UUID.randomUUID().toString(), false, false),
        Arguments.of(user.substring(0, user.length() - 1), false, false),
        Arguments.of(user + "0", false, false),
        Arguments.of(user + "\n", false, false),
        Arguments.of(user.toUpperCase(Locale.ROOT), false, false));
  }

  private static Stream<Arguments> invalidIdentities() {
    return Stream.of(
        Arguments.of(ActorType.USER, null, "subject", NullPointerException.class),
        Arguments.of(
            ActorType.SERVICE, "https://issuer.example.test", null, NullPointerException.class),
        Arguments.of(ActorType.USER, " ", "subject", IllegalArgumentException.class),
        Arguments.of(
            ActorType.SERVICE,
            "https://issuer.example.test",
            "\t",
            IllegalArgumentException.class));
  }

  private static String encode(final ActorType type, final String issuer, final String subject) {
    return switch (type) {
      case USER -> OidcPrincipalIdEncoder.userId(issuer, subject);
      case SERVICE -> OidcPrincipalIdEncoder.serviceId(issuer, subject);
      default ->
          throw new IllegalArgumentException(
              "Only external user and service fixtures are supported");
    };
  }
}
