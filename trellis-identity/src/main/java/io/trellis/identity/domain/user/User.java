package io.trellis.identity.domain.user;

import java.time.Instant;

/** Native user metadata; password material is deliberately held in a separate type and table. */
public record User(
    Long id,
    String email,
    String displayName,
    UserStatus status,
    Instant emailVerifiedAt,
    Instant createdAt,
    Instant updatedAt) {}
