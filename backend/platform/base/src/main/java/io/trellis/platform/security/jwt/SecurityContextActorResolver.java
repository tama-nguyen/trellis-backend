package io.trellis.platform.security.jwt;

import io.trellis.common.context.Actor;
import io.trellis.platform.security.ActorResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Current actor from Spring Security's context; anonymous when there is no JWT authentication. */
public final class SecurityContextActorResolver implements ActorResolver {
    private final ClaimActorMapper mapper;

    public SecurityContextActorResolver(ClaimActorMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Actor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token && token.isAuthenticated()) {
            return mapper.toActor(token.getToken());
        }
        return Actor.anonymous();
    }
}
