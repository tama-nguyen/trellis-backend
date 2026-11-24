package io.trellis.identity.infrastructure.security;

import io.trellis.common.context.Actor;
import io.trellis.identity.IdentityProperties;
import io.trellis.identity.application.port.TokenCodec;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.domain.token.TokenType;
import io.trellis.identity.domain.user.UserStatus;
import io.trellis.platform.security.ActorBearerAuthenticationToken;
import java.time.Clock;
import java.util.Set;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.stereotype.Component;

/** Resolves native opaque access tokens; non-native bearer tokens are left for the JWT provider. */
@Component
public class NativeTokenAuthenticationProvider implements AuthenticationProvider {
  private static final String INVALID_BEARER = "Invalid bearer token";

  private final UserRepository identities;
  private final TokenCodec tokens;
  private final IdentityProperties properties;
  private final Clock clock;

  public NativeTokenAuthenticationProvider(
      UserRepository identities, TokenCodec tokens, IdentityProperties properties, Clock clock) {
    this.identities = identities;
    this.tokens = tokens;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public Authentication authenticate(Authentication authentication) {
    String token = ((BearerTokenAuthenticationToken) authentication).getToken();
    if (!tokens.isType(token, TokenType.ACCESS)) {
      return null;
    }
    var user =
        identities
            .userByActiveAccessToken(tokens.hash(token), clock.instant())
            .orElseThrow(() -> new BadCredentialsException(INVALID_BEARER));
    if (user.status() == UserStatus.DISABLED) {
      throw new DisabledException(INVALID_BEARER);
    }
    if (user.status() != UserStatus.ACTIVE) {
      throw new BadCredentialsException(INVALID_BEARER);
    }
    Actor actor = Actor.user(user.id().toString(), Set.of(properties.defaultRole()), Set.of());
    return new ActorBearerAuthenticationToken(actor);
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return BearerTokenAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
