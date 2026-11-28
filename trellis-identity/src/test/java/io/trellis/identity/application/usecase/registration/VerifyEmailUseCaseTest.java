package io.trellis.identity.application.usecase.registration;

import static io.trellis.identity.IdentityTestFixtures.NOW;
import static io.trellis.identity.IdentityTestFixtures.anonymousContext;
import static io.trellis.identity.IdentityTestFixtures.user;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.trellis.common.error.TrellisException;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.support.ActionTokens;
import io.trellis.identity.domain.action.UserActionToken;
import io.trellis.identity.domain.action.UserActionType;
import io.trellis.identity.domain.user.UserStatus;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerifyEmailUseCaseTest {
  @Mock private UserRepository userRepository;

  @Mock private ActionTokens actionTokens;

  private VerifyEmailUseCase verifyEmailUseCase;

  @BeforeEach
  void setUp() {
    verifyEmailUseCase = new VerifyEmailUseCase(userRepository, actionTokens);
  }

  @Test
  void verifiesUserAndConsumesActionToken() {
    // Given
    val user = user(UserStatus.PENDING_VERIFICATION);
    val actionId = 1L;
    when(actionTokens.lockActive("raw-action", UserActionType.VERIFY_EMAIL, NOW))
        .thenReturn(new UserActionToken(actionId, user, UserActionType.VERIFY_EMAIL));

    // When
    verifyEmailUseCase.execute(anonymousContext(), "raw-action");

    // Then
    verify(userRepository).verifyEmail(user.id(), NOW);
    verify(userRepository).consumeActionToken(actionId, NOW);
  }

  @Test
  void invalidActionDoesNotMutateUser() {
    // Given
    when(actionTokens.lockActive("invalid", UserActionType.VERIFY_EMAIL, NOW))
        .thenThrow(
            new TrellisException(
                io.trellis.identity.api.error.IdentityErrorCode.ACTION_TOKEN_INVALID, "invalid"));

    // When
    assertThrows(
        TrellisException.class, () -> verifyEmailUseCase.execute(anonymousContext(), "invalid"));

    // Then
    verifyNoInteractions(userRepository);
  }
}
