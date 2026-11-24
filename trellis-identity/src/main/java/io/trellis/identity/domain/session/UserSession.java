package io.trellis.identity.domain.session;

import io.trellis.identity.domain.user.User;
import java.time.Instant;
import java.util.UUID;

/** Locked session row used for refresh-token rotation. */
public record UserSession(UUID id, User user, Instant refreshExpiresAt) {}
