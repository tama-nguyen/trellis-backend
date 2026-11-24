package io.trellis.common.security;

/** Result of an authorization check. {@code reason} is for logs and audit, never for the client. */
public record AuthorizationDecision(boolean allowed, String reason) {
  public static AuthorizationDecision allow(String reason) {
    return new AuthorizationDecision(true, reason);
  }

  public static AuthorizationDecision deny(String reason) {
    return new AuthorizationDecision(false, reason);
  }
}
