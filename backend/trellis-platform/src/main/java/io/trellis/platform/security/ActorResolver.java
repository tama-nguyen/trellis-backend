package io.trellis.platform.security;

import io.trellis.common.context.Actor;

/**
 * Resolves the {@link Actor} of the current request. The security module provides the real implementation
 * from the authenticated principal; without it every request is anonymous.
 */
@FunctionalInterface
public interface ActorResolver {
    Actor currentActor();
}
