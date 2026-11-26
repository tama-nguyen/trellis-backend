package io.trellis.platform.security;

import io.trellis.common.context.Actor;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/** Authenticated actor principal shared by native bearer and trusted user delegation. */
public final class ActorBearerAuthenticationToken extends AbstractAuthenticationToken
    implements ActorAuthentication {
  private final Actor actor;

  public ActorBearerAuthenticationToken(Actor actor) {
    super(List.of());
    this.actor = actor;
    setAuthenticated(true);
  }

  @Override
  public Actor actor() {
    return actor;
  }

  @Override
  public Object getCredentials() {
    return "";
  }

  @Override
  public Object getPrincipal() {
    return actor.id();
  }
}
