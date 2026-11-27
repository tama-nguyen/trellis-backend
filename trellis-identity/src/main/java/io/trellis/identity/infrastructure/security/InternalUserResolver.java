
package io.trellis.identity.infrastructure.security;

import io.trellis.common.context.Actor;
import io.trellis.common.context.IdentityOrigin;
import io.trellis.identity.IdentityProperties;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserStatus;
import io.trellis.platform.security.DelegatedUserResolver;
import io.trellis.platform.security.jwt.OidcPrincipalIdEncoder;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
final class InternalUserResolver implements DelegatedUserResolver {

  private static final String INVALID_USER = "Invalid delegated user";

  private final UserRepository userRepository;
  private final IdentityProperties identityProperties;

  @Override
  public Actor.UserActor resolve(final String userId) {
    if (OidcPrincipalIdEncoder.isUserId(userId)) {
      // A service credential cannot supply JWT roles or direct permissions.
      return new Actor.UserActor(
        userId, Set.of(), Set.of(), IdentityOrigin.OIDC);
    }

    final UUID nativeUserId;

    try {
      nativeUserId = UUID.fromString(userId);
    } catch (IllegalArgumentException error) {
      throw new BadCredentialsException(INVALID_USER);
    }

    if (!nativeUserId.toString().equals(userId)) {
      throw new BadCredentialsException(INVALID_USER);
    }

    final User user =
      userRepository
        .userById(nativeUserId)
        .orElseThrow(() -> new BadCredentialsException(INVALID_USER));

    if (user.status() != UserStatus.ACTIVE) {
      throw new BadCredentialsException(INVALID_USER);
    }

    return new Actor.UserActor(
      user.id().toString(),
      Set.of(identityProperties.defaultRole()),
      Set.of());
  }
}
