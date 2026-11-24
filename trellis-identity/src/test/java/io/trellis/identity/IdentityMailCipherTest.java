package io.trellis.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.trellis.identity.application.view.IdentityMail;
import io.trellis.identity.domain.action.UserActionType;
import io.trellis.identity.infrastructure.mail.IdentityMailCipher;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class IdentityMailCipherTest {
  private static final int KEY_BYTES = 32;
  private static final String KEY_VERSION = "v1";

  @Test
  void shouldEncryptSensitiveMailFields() {
    // Given
    val cipher = cipher(new byte[KEY_BYTES]);
    val mail = verificationMail();

    // When
    val encrypted = cipher.encrypt(mail);

    // Then
    assertFalse(encrypted.contains(mail.email()));
    assertFalse(encrypted.contains(mail.secret()));
  }

  @Test
  void shouldGenerateDifferentCiphertextsForTheSameMail() {
    // Given
    val cipher = cipher(new byte[KEY_BYTES]);
    val mail = verificationMail();

    // When
    val first = cipher.encrypt(mail);
    val second = cipher.encrypt(mail);

    // Then
    assertNotEquals(first, second);
  }

  @Test
  void shouldDecryptTheOriginalMail() {
    // Given
    val cipher = cipher(new byte[KEY_BYTES]);
    val mail = verificationMail();
    val encrypted = cipher.encrypt(mail);

    // When
    val decrypted = cipher.decrypt(KEY_VERSION, encrypted);

    // Then
    assertEquals(mail, decrypted);
  }

  @Test
  void shouldRejectUnsupportedKeyVersion() {
    // Given
    val cipher = cipher(new byte[KEY_BYTES]);
    val encrypted = cipher.encrypt(verificationMail());

    // When & Then
    assertThrows(IllegalStateException.class, () -> cipher.decrypt("v2", encrypted));
  }

  @Test
  void shouldRejectCiphertextEncryptedWithAnotherKey() {
    // Given
    val encrypted = cipher(new byte[KEY_BYTES]).encrypt(verificationMail());
    val anotherKey = new byte[KEY_BYTES];
    anotherKey[0] = 1;
    val anotherCipher = cipher(anotherKey);

    // When & Then
    assertThrows(IllegalStateException.class, () -> anotherCipher.decrypt(KEY_VERSION, encrypted));
  }

  @Test
  void shouldRejectTamperedCiphertext() {
    // Given
    val cipher = cipher(new byte[KEY_BYTES]);
    val encrypted = cipher.encrypt(verificationMail());
    val corrupted = corruptPayload(encrypted);

    // When & Then
    assertThrows(IllegalStateException.class, () -> cipher.decrypt(KEY_VERSION, corrupted));
  }

  @Test
  void shouldRedactSecretFromMailRendering() {
    // Given
    val mail = verificationMail();

    // When
    val rendered = mail.toString();

    // Then
    assertFalse(rendered.contains(mail.secret()));
  }

  private IdentityMail verificationMail() {
    return new IdentityMail(
        UserActionType.VERIFY_EMAIL, "user@example.test", "Display name", "secret-link-token");
  }

  private String corruptPayload(final String encrypted) {
    val parts = encrypted.split("\\.");
    val payload = Base64.getDecoder().decode(parts[1]);
    payload[0] ^= 1;
    return parts[0] + "." + Base64.getEncoder().encodeToString(payload);
  }

  private IdentityMailCipher cipher(final byte[] key) {
    val properties = new IdentityMailProperties();
    ReflectionTestUtils.setField(
        properties, "encryptionKey", Base64.getEncoder().encodeToString(key));
    ReflectionTestUtils.setField(properties, "keyVersion", KEY_VERSION);
    return new IdentityMailCipher(properties, new SecureRandom(), JsonMapper.builder().build());
  }
}
