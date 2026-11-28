package io.trellis.identity.domain.action;

import io.trellis.identity.domain.user.User;

/** Locked one-time action token joined to its user. */
public record UserActionToken(Long id, User user, UserActionType type) {}
