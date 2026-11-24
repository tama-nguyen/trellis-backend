package io.trellis.platform.security.jwt;

import io.trellis.common.context.Actor;
import io.trellis.platform.security.ActorAuthentication;
import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** JWT authentication that carries the single actor instance created by the authentication converter. */
public final class ActorJwtAuthenticationToken extends JwtAuthenticationToken implements ActorAuthentication {
    private final Actor actor;

    public ActorJwtAuthenticationToken(Jwt jwt, Actor actor) {
        super(jwt, List.of(), actor.id());
        this.actor = actor;
    }

    @Override
    public Actor actor() {
        return actor;
    }
}
