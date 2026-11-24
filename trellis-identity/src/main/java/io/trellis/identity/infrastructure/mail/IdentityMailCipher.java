package io.trellis.identity.infrastructure.mail;

import io.trellis.identity.IdentityMailProperties;
import io.trellis.identity.application.view.IdentityMail;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class IdentityMailCipher {
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;
  private final IdentityMailProperties properties;
  private final SecureRandom random;
  private final SecretKeySpec key;
  private final JsonMapper json;

  public IdentityMailCipher(
      IdentityMailProperties properties, SecureRandom random, JsonMapper json) {
    this.properties = properties;
    this.random = random;
    this.json = json;
    byte[] bytes = Base64.getDecoder().decode(properties.encryptionKey());
    if (bytes.length != 32)
      throw new IllegalArgumentException("Identity mail requires a 256-bit key");
    this.key = new SecretKeySpec(bytes, "AES");
  }

  public String encrypt(IdentityMail mail) {
    byte[] nonce = new byte[NONCE_BYTES];
    random.nextBytes(nonce);
    try {
      Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce);
      byte[] encrypted = cipher.doFinal(json.writeValueAsBytes(mail));
      return Base64.getEncoder().encodeToString(nonce)
          + "."
          + Base64.getEncoder().encodeToString(encrypted);
    } catch (GeneralSecurityException error) {
      throw new IllegalStateException("Cannot encrypt identity mail");
    }
  }

  public IdentityMail decrypt(String version, String payload) {
    if (!properties.keyVersion().equals(version))
      throw new IllegalStateException("Identity mail key version unavailable");
    String[] parts = payload.split("\\.", -1);
    if (parts.length != 2) throw new IllegalStateException("Invalid encrypted identity mail");
    try {
      byte[] nonce = Base64.getDecoder().decode(parts[0]);
      if (nonce.length != NONCE_BYTES)
        throw new IllegalStateException("Invalid identity mail nonce");
      byte[] plain =
          cipher(Cipher.DECRYPT_MODE, nonce).doFinal(Base64.getDecoder().decode(parts[1]));
      return json.readValue(plain, IdentityMail.class);
    } catch (GeneralSecurityException error) {
      throw new IllegalStateException("Cannot decrypt identity mail");
    }
  }

  private Cipher cipher(int mode, byte[] nonce) throws GeneralSecurityException {
    Cipher cipher = Cipher.getInstance(ALGORITHM);
    cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
    cipher.updateAAD(properties.keyVersion().getBytes(StandardCharsets.UTF_8));
    return cipher;
  }
}
