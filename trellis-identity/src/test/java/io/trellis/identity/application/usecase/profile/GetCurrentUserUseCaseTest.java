package io.trellis.identity.application.usecase.profile;

import static io.trellis.identity.IdentityTestFixtures.user;
import static io.trellis.identity.IdentityTestFixtures.userContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.trellis.common.security.Permission;
import io.trellis.common.security.ResourceScope;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.view.AuthenticatedUser;
import io.trellis.identity.domain.user.UserStatus;
import io.trellis.platform.security.authz.PermissionResolver;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetCurrentUserUseCaseTest {
  @Mock private UserRepository userRepository;

  @Mock private PermissionResolver permissionResolver;

  private GetCurrentUserUseCase getCurrentUserUseCase;

  @BeforeEach
  void setUp() {
    getCurrentUserUseCase = new GetCurrentUserUseCase(userRepository, permissionResolver);
  }

  @Test
  void nativeUserDataOverridesIdentityProviderClaims() {
    // Given
    val user = user(UserStatus.ACTIVE);
    val context = userContext(user.id().toString());
    when(userRepository.userById(user.id())).thenReturn(Optional.of(user));
    when(permissionResolver.permissionsOf(context.actor(), ResourceScope.global()))
        .thenReturn(Set.of(Permission.of("notice:read"), Permission.of("course:manage")));

    // When
    val profile = getCurrentUserUseCase.execute(context, "claim@example.test", "Claim Name");

    // Then
    assertEquals(
        new AuthenticatedUser(
            user.id().toString(),
            user.email(),
            user.displayName(),
            List.of("course:manage", "notice:read")),
        profile);
  }

  @Test
  void oidcSubjectUsesClaimsWithoutNativeLookup() {
    // Given
    val context = userContext("keycloak-subject");
    when(permissionResolver.permissionsOf(context.actor(), ResourceScope.global()))
        .thenReturn(Set.of());

    // When
    val profile = getCurrentUserUseCase.execute(context, "oidc@example.test", "OIDC User");

    // Then
    assertEquals(
        new AuthenticatedUser("keycloak-subject", "oidc@example.test", "OIDC User", List.of()),
        profile);
    verifyNoInteractions(userRepository);
  }

  @Test
  void missingOidcClaimsFallBackToSafeValues() {
    // Given
    val context = userContext("keycloak-subject");
    when(permissionResolver.permissionsOf(context.actor(), ResourceScope.global()))
        .thenReturn(Set.of());

    // When
    val profile = getCurrentUserUseCase.execute(context, null, "  ");

    // Then
    assertEquals(
        new AuthenticatedUser("keycloak-subject", "", "keycloak-subject", List.of()), profile);
  }
}
