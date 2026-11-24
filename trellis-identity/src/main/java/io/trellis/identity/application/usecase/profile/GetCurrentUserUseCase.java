package io.trellis.identity.application.usecase.profile;

import io.trellis.common.context.RequestContext;
import io.trellis.common.security.Permission;
import io.trellis.common.security.ResourceScope;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.view.AuthenticatedUser;
import io.trellis.identity.domain.user.User;
import io.trellis.platform.security.authz.PermissionResolver;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads back whoever is calling, native user or OIDC subject, with the permissions they hold now. */
@Service
public class GetCurrentUserUseCase {
    private final UserRepository users;
    private final PermissionResolver permissions;

    public GetCurrentUserUseCase(UserRepository users, PermissionResolver permissions) {
        this.users = users;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser execute(RequestContext ctx, String claimEmail, String claimDisplayName) {
        Optional<User> user = nativeUser(ctx.actor().id());
        String email = user.map(User::email).orElseGet(() -> claimEmail == null ? "" : claimEmail);
        String displayName = user.map(User::displayName)
                .orElseGet(() -> claimDisplayName == null || claimDisplayName.isBlank()
                        ? ctx.actor().id()
                        : claimDisplayName);
        List<String> granted = permissions.permissionsOf(ctx.actor(), ResourceScope.global()).stream()
                .map(Permission::value)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new AuthenticatedUser(ctx.actor().id(), email, displayName, granted);
    }

    /** An OIDC subject is not a UUID, so a non-UUID actor id simply has no native user. */
    private Optional<User> nativeUser(String actorId) {
        try {
            return users.userById(UUID.fromString(actorId));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }
}
