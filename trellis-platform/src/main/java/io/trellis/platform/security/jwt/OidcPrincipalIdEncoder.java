package io.trellis.platform.security.jwt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Encodes issuer-scoped identities from OpenID Connect (OIDC) providers.
 *
 * <p>The bundled local stack uses Keycloak as its OIDC provider. Encoded IDs are used by persisted
 * grants and resource ownership.
 */
public final class OidcPrincipalIdEncoder {
  private static final String USER_PREFIX = "oidc-user";
  private static final String SERVICE_PREFIX = "oidc-service";
  private static final String SEPARATOR = ":";
  private static final String DIGEST_ALGORITHM = "SHA-256";
  private static final String ENCODED_DIGEST = "[0-9a-f]{64}";
  private static final Pattern USER_ID = Pattern.compile(USER_PREFIX + SEPARATOR + ENCODED_DIGEST);
  private static final Pattern SERVICE_ID =
      Pattern.compile(SERVICE_PREFIX + SEPARATOR + ENCODED_DIGEST);

  private OidcPrincipalIdEncoder() {}

  /** Checks the stored format only; external existence/provenance remains an operator concern. */
  public static boolean isUserId(final String principalId) {
    return principalId != null && USER_ID.matcher(principalId).matches();
  }

  public static boolean isServiceId(final String principalId) {
    return principalId != null && SERVICE_ID.matcher(principalId).matches();
  }

  public static String userId(final String issuer, final String subject) {
    return encode(USER_PREFIX, issuer, subject);
  }

  public static String serviceId(final String issuer, final String clientId) {
    return encode(SERVICE_PREFIX, issuer, clientId);
  }

  private static String encode(final String prefix, final String issuer, final String subject) {
    requireNonBlank(issuer);
    requireNonBlank(subject);
    // Persisted IDs use Java's UTF-16 length prefix. Changing it would require data migration.
    final String input = issuer.length() + SEPARATOR + issuer + subject;

    try {
      final byte[] digest =
          MessageDigest.getInstance(DIGEST_ALGORITHM)
              .digest(input.getBytes(StandardCharsets.UTF_8));

      return prefix + SEPARATOR + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is required", error);
    }
  }

  private static void requireNonBlank(final String value) {
    if (Objects.requireNonNull(value, "value").isBlank()) {
      throw new IllegalArgumentException("OIDC identity value must not be blank");
    }
  }
}
