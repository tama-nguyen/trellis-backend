package io.trellis.platform.security;

import io.trellis.common.context.Actor;

/** Authentication result that carries the actor constructed exactly once for the request. */
public interface ActorAuthentication {
    Actor actor();
}
