package io.trellis.identity.application.usecase.profile;

import static io.trellis.identity.IdentityTestFixtures.user;
import static io.trellis.identity.IdentityTestFixtures.userContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.trellis.common.context.RequestContext;
import io.trellis.common.security.Permission;
import io.trellis.common.security.ResourceScope;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.view.AuthenticatedUser;
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserStatus;
import io.trellis.platform.security.authz.PermissionResolver;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetCurrentUserUseCaseTest {
    @Mock
    private UserRepository users;

    @Mock
    private PermissionResolver permissions;

    private GetCurrentUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetCurrentUserUseCase(users, permissions);
    }

    @Test
    void nativeUserDataOverridesIdentityProviderClaims() {
        User user = user(UserStatus.ACTIVE);
        RequestContext context = userContext(user.id().toString());
        when(users.userById(user.id())).thenReturn(Optional.of(user));
        when(permissions.permissionsOf(context.actor(), ResourceScope.global()))
                .thenReturn(Set.of(Permission.of("notice:read"), Permission.of("course:manage")));

        AuthenticatedUser result = useCase.execute(context, "claim@example.test", "Claim Name");

        assertEquals(
                new AuthenticatedUser(
                        user.id().toString(),
                        user.email(),
                        user.displayName(),
                        List.of("course:manage", "notice:read")),
                result);
    }

    @Test
    void oidcSubjectUsesClaimsWithoutNativeLookup() {
        RequestContext context = userContext("keycloak-subject");
        when(permissions.permissionsOf(context.actor(), ResourceScope.global())).thenReturn(Set.of());

        AuthenticatedUser result = useCase.execute(context, "oidc@example.test", "OIDC User");

        assertEquals(new AuthenticatedUser("keycloak-subject", "oidc@example.test", "OIDC User", List.of()), result);
        verifyNoInteractions(users);
    }

    @Test
    void missingOidcClaimsFallBackToSafeValues() {
        RequestContext context = userContext("keycloak-subject");
        when(permissions.permissionsOf(context.actor(), ResourceScope.global())).thenReturn(Set.of());

        AuthenticatedUser result = useCase.execute(context, null, "  ");

        assertEquals(new AuthenticatedUser("keycloak-subject", "", "keycloak-subject", List.of()), result);
    }
}
