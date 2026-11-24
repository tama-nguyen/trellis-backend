package io.trellis.identity.application.port;

/** Sends user-action links. Implementations must never log the raw token or full link. */
public interface UserMailSender {
  void sendEmailVerification(String email, String displayName, String token);

  void sendPasswordResetCode(String email, String displayName, String code);
}
