package io.trellis.common.id;

import io.trellis.common.json.CanonicalJson;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic id derived from content: the same namespace and fields always give the same UUID,
 * in Java and in Python. Use it for natural keys that arrive from outside (an imported row, a
 * dedupe key).
 *
 * <p>Algorithm (the Python worker must match; vectors in {@code
 * contracts/testdata/stable-id.json}): {@code digest = SHA-256(utf8(namespace) || 0x00 ||
 * utf8(canonical_json(fields)))} (see {@link CanonicalJson}); take the first 16 bytes, set the
 * version nibble to 8 and the RFC 9562 variant bits.
 */
public final class StableId {
  private static final byte SEPARATOR = 0x00;
  private static final int UUID_BYTES = 16;
  private static final int VERSION_BYTE = 6;
  private static final int VARIANT_BYTE = 8;

  private StableId() {}

  public static UUID of(String namespace, Map<String, ?> fields) {
    if (namespace == null || namespace.isBlank()) {
      throw new IllegalArgumentException("namespace is required");
    }
    byte[] ns = namespace.getBytes(StandardCharsets.UTF_8);
    byte[] body = CanonicalJson.write(fields).getBytes(StandardCharsets.UTF_8);
    byte[] input =
        ByteBuffer.allocate(ns.length + 1 + body.length).put(ns).put(SEPARATOR).put(body).array();
    byte[] digest = sha256(input);
    byte[] bytes = new byte[UUID_BYTES];
    System.arraycopy(digest, 0, bytes, 0, UUID_BYTES);
    bytes[VERSION_BYTE] = (byte) ((bytes[VERSION_BYTE] & 0x0F) | 0x80);
    bytes[VARIANT_BYTE] = (byte) ((bytes[VARIANT_BYTE] & 0x3F) | 0x80);
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    return new UUID(buffer.getLong(), buffer.getLong());
  }

  private static byte[] sha256(byte[] input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is mandatory on every JVM", e);
    }
  }
}
