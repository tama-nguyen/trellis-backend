package io.trellis.identity.domain.user;

import java.time.Instant;

/** Account state before the database assigns its identifier. */
public record NewUser(
    String email,
    String displayName,
    UserStatus status,
    Instant emailVerifiedAt,
    Instant createdAt,
    Instant updatedAt) {}
