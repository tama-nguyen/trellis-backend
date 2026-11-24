package io.trellis.identity.domain.action;

import io.trellis.identity.domain.user.User;
import java.util.UUID;

/** Locked one-time action token joined to its user. */
public record UserActionToken(UUID id, User user, UserActionType type) {}
