package io.trellis.identity.application.usecase.profile;

import io.trellis.common.context.Actor;
import io.trellis.common.context.IdentityOrigin;
import io.trellis.common.context.RequestContext;
import io.trellis.common.id.EntityIds;
import io.trellis.common.security.Permission;
import io.trellis.common.security.ResourceScope;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.view.AuthenticatedUser;
import io.trellis.identity.domain.user.User;
import io.trellis.platform.security.authz.PermissionResolver;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads back whoever is calling, native user or OIDC subject, with the permissions they hold now.
 */
@Service
@RequiredArgsConstructor
public class GetCurrentUserUseCase {
  private final UserRepository userRepository;
  private final PermissionResolver permissionResolver;

  @Transactional(readOnly = true)
  public AuthenticatedUser execute(
      final RequestContext ctx, final String claimEmail, final String claimDisplayName) {
    final Actor actor = ctx.actor();
    final Optional<User> nativeAccount =
        actor instanceof Actor.UserActor userActor && userActor.origin() == IdentityOrigin.NATIVE
            ? findNativeUser(userActor.id())
            : Optional.empty();
    final String email =
        nativeAccount.map(User::email).orElseGet(() -> claimEmail == null ? "" : claimEmail);
    final String displayName =
        nativeAccount
            .map(User::displayName)
            .orElseGet(() -> displayNameFromClaims(actor, claimDisplayName));

    final List<String> grantedPermissions =
        permissionResolver.permissionsOf(actor, ResourceScope.global()).stream()
            .map(Permission::value)
            .sorted(Comparator.naturalOrder())
            .toList();

    return new AuthenticatedUser(actor.id(), email, displayName, grantedPermissions);
  }

  private String displayNameFromClaims(final Actor actor, final String claimDisplayName) {
    if (claimDisplayName == null || claimDisplayName.isBlank()) {
      return actor.id();
    }
    return claimDisplayName;
  }

  /**
   * Native account IDs are canonical positive decimals; identity provenance is checked before this
   * lookup.
   */
  private Optional<User> findNativeUser(final String actorId) {
    try {
      return userRepository.userById(EntityIds.parse(actorId));
    } catch (IllegalArgumentException invalidId) {
      return Optional.empty();
    }
  }
}
