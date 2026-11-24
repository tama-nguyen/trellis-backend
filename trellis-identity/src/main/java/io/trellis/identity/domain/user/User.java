package io.trellis.identity.domain.user;

import java.time.Instant;
import java.util.UUID;

/** Native user metadata; password material is deliberately held in a separate type and table. */
public record User(
        UUID id,
        String email,
        String displayName,
        UserStatus status,
        Instant emailVerifiedAt,
        Instant createdAt,
        Instant updatedAt) {}
