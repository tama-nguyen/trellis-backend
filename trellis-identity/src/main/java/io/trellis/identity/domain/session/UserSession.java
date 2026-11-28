package io.trellis.identity.domain.session;

import io.trellis.identity.domain.user.User;
import java.time.Instant;

/** Locked session row used for refresh-token rotation. */
public record UserSession(Long id, User user, Instant refreshExpiresAt) {}
