package io.trellis.platform.security.jwt;

import io.trellis.common.context.Actor;
import io.trellis.platform.security.ActorAuthentication;
import io.trellis.platform.security.ActorResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Current actor from Spring Security's context; anonymous when no supported authentication succeeded. */
public final class SecurityContextActorResolver implements ActorResolver {
    @Override
    public Actor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof ActorAuthentication actorAuthentication && authentication.isAuthenticated()) {
            return actorAuthentication.actor();
        }
        return Actor.anonymous();
    }
}
