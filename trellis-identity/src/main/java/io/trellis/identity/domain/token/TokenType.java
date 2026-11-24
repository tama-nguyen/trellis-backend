package io.trellis.identity.domain.token;

/** Native secret families; their wire prefixes are owned by the crypto adapter. */
public enum TokenType {
    ACCESS,
    REFRESH,
    ACTION
}
