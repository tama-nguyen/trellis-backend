package io.trellis.identity.application.port;

import io.trellis.identity.domain.token.TokenType;

/** Generates typed random secrets and derives their one-way storage hashes. */
public interface TokenCodec {
  SecretToken issue(TokenType type);

  String hash(String raw);

  boolean isType(String raw, TokenType type);

  record SecretToken(String raw, String hash) {}
}
