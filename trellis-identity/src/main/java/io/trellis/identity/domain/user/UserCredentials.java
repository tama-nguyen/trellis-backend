package io.trellis.identity.domain.user;

/** User plus adaptive password hash, loaded only by the login use case. */
public record UserCredentials(User user, String passwordHash) {}
