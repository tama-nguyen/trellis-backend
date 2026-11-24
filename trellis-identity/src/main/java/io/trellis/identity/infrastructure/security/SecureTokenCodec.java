package io.trellis.identity.infrastructure.security;

import io.trellis.identity.application.port.TokenCodec;
import io.trellis.identity.domain.token.TokenType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Generates bearer secrets and exposes only their deterministic SHA-256 storage hash. */
public final class SecureTokenCodec implements TokenCodec {
  private static final String ACCESS_PREFIX = "tr_at_";
  private static final String REFRESH_PREFIX = "tr_rt_";
  private static final String ACTION_PREFIX = "tr_ac_";
  private static final String HASH_ALGORITHM = "SHA-256";
  private static final int TOKEN_BYTES = 32;

  private final SecureRandom random;

  public SecureTokenCodec(SecureRandom random) {
    this.random = random;
  }

  @Override
  public SecretToken issue(TokenType type) {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    String prefix = prefix(type);
    String raw = prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    return new SecretToken(raw, hash(raw));
  }

  @Override
  public String hash(String raw) {
    try {
      byte[] digest =
          MessageDigest.getInstance(HASH_ALGORITHM).digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException(HASH_ALGORITHM + " must be available", error);
    }
  }

  @Override
  public boolean isType(String raw, TokenType type) {
    return raw.startsWith(prefix(type));
  }

  private static String prefix(TokenType type) {
    return switch (type) {
      case ACCESS -> ACCESS_PREFIX;
      case REFRESH -> REFRESH_PREFIX;
      case ACTION -> ACTION_PREFIX;
    };
  }
}
