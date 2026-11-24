package io.trellis.common.page;

import io.trellis.common.error.PlatformErrorCode;
import io.trellis.common.error.TrellisException;
import io.trellis.common.json.CanonicalJson;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encodes {@link Cursor}s into opaque, URL-safe tokens and back. Same format in Java, Python and
 * TypeScript.
 *
 * <p>Format v1: {@code base64url(canonical_json({"k": [keys...], "v": 1}))}, then, when a secret is
 * configured, {@code "." + base64url(first 16 bytes of HMAC-SHA256(secret, json))}. Base64url has
 * no padding. The signature stops clients from crafting cursors that probe other rows; it is not
 * encryption, so keys must not be secret.
 */
public final class CursorCodec {
  static final int VERSION = 1;
  private static final String VERSION_FIELD = "v";
  private static final String KEYS_FIELD = "k";
  private static final String SIGNATURE_SEPARATOR = ".";
  private static final String HMAC = "HmacSHA256";
  private static final int SIGNATURE_BYTES = 16;
  private static final int MAX_TOKEN_LENGTH = 2048;

  private final byte[] secret;

  private CursorCodec(byte[] secret) {
    this.secret = secret;
  }

  /** Unsigned cursors: fine for internal tools, not for public APIs. */
  public static CursorCodec unsigned() {
    return new CursorCodec(null);
  }

  public static CursorCodec signed(byte[] secret) {
    if (secret == null || secret.length < SIGNATURE_BYTES) {
      throw new IllegalArgumentException("Cursor secret must be at least 16 bytes");
    }
    return new CursorCodec(secret.clone());
  }

  public String encode(Cursor cursor) {
    String json = CanonicalJson.write(Map.of(KEYS_FIELD, cursor.keys(), VERSION_FIELD, VERSION));
    String body = base64(json.getBytes(StandardCharsets.UTF_8));
    return secret == null ? body : body + SIGNATURE_SEPARATOR + base64(sign(json));
  }

  /**
   * @throws TrellisException with {@code INVALID_CURSOR} for any malformed, tampered or
   *     unknown-version token
   */
  public Cursor decode(String token) {
    if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
      throw invalid();
    }
    try {
      int dot = token.indexOf(SIGNATURE_SEPARATOR);
      String body = dot < 0 ? token : token.substring(0, dot);
      String json = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8);
      if (secret != null) {
        if (dot < 0) {
          throw invalid();
        }
        byte[] given = Base64.getUrlDecoder().decode(token.substring(dot + 1));
        if (!MessageDigest.isEqual(given, sign(json))) {
          throw invalid();
        }
      }
      return parse(json);
    } catch (IllegalArgumentException | ClassCastException e) {
      throw invalid();
    }
  }

  private static Cursor parse(String json) {
    if (!(CanonicalJson.parse(json) instanceof Map<?, ?> map)) {
      throw invalid();
    }
    Object version = map.get(VERSION_FIELD);
    if (!(version instanceof Number n) || n.intValue() != VERSION) {
      throw invalid();
    }
    if (!(map.get(KEYS_FIELD) instanceof List<?> rawKeys)) {
      throw invalid();
    }
    List<String> keys = new ArrayList<>(rawKeys.size());
    for (Object key : rawKeys) {
      keys.add((String) key);
    }
    return new Cursor(keys);
  }

  private byte[] sign(String json) {
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(secret, HMAC));
      return Arrays.copyOf(mac.doFinal(json.getBytes(StandardCharsets.UTF_8)), SIGNATURE_BYTES);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 is mandatory on every JVM", e);
    }
  }

  private static String base64(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static TrellisException invalid() {
    return new TrellisException(
        PlatformErrorCode.INVALID_CURSOR, "The page cursor is invalid or expired");
  }
}
