package io.trellis.identity.domain.model;

/** User plus adaptive password hash, loaded only by the login use case. */
public record UserCredentials(User user, String passwordHash) {}
