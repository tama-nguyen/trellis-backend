package io.trellis.identity.domain.model;

/** Native secret families; their wire prefixes are owned by the crypto adapter. */
public enum TokenType {
    ACCESS,
    REFRESH,
    ACTION
}
