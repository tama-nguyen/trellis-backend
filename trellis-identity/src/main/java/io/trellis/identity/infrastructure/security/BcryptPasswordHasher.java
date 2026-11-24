package io.trellis.identity.infrastructure.security;

import io.trellis.identity.application.port.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;

/** BCrypt adapter for the password hashing boundary. */
public final class BcryptPasswordHasher implements PasswordHasher {
  private final PasswordEncoder encoder;

  public BcryptPasswordHasher(PasswordEncoder encoder) {
    this.encoder = encoder;
  }

  @Override
  public String hash(String password) {
    return encoder.encode(password);
  }

  @Override
  public boolean matches(String password, String hash) {
    return encoder.matches(password, hash);
  }
}
