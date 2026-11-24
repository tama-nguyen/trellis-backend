package io.trellis.identity.application.port;

/** Adaptive password hashing boundary. */
public interface PasswordHasher {
  String hash(String password);

  boolean matches(String password, String hash);
}
