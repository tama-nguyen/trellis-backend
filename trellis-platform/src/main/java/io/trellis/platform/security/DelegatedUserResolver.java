package io.trellis.platform.security;

import io.trellis.common.context.Actor;

/** Resolves a user for a caller authenticated by the configured internal service credential. */
@FunctionalInterface
public interface DelegatedUserResolver {
  Actor.UserActor resolve(String userId);
}
