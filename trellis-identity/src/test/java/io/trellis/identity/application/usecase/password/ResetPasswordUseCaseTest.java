package io.trellis.identity.application.usecase.password;

import static io.trellis.identity.IdentityTestFixtures.NOW;
import static io.trellis.identity.IdentityTestFixtures.anonymousContext;
import static io.trellis.identity.IdentityTestFixtures.user;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.trellis.common.error.TrellisException;
import io.trellis.identity.api.error.IdentityErrorCode;
import io.trellis.identity.application.port.PasswordHasher;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.support.PasswordResetCodes;
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
class ResetPasswordUseCaseTest {
  @Mock private UserRepository userRepository;

  @Mock private PasswordHasher passwordHasher;

  @Mock private PasswordResetCodes passwordResetCodes;

  private ResetPasswordUseCase resetPasswordUseCase;

  @BeforeEach
  void setUp() {
    resetPasswordUseCase =
        new ResetPasswordUseCase(userRepository, passwordHasher, passwordResetCodes);
  }

  @Test
  void changesPasswordRevokesSessionsAndConsumesAction() {
    // Given
    val user = user(UserStatus.ACTIVE);
    val actionId = 1L;
    when(passwordResetCodes.lockActive(user.email(), "PW-1234-5678-9ABC", NOW))
        .thenReturn(new UserActionToken(actionId, user, UserActionType.RESET_PASSWORD));
    when(passwordHasher.hash("new-password")).thenReturn("new-password-hash");

    // When
    resetPasswordUseCase.execute(
        anonymousContext(), user.email(), "PW-1234-5678-9ABC", "new-password");

    // Then
    verify(userRepository).changePassword(user.id(), "new-password-hash", NOW);
    verify(userRepository).revokeSessions(user.id(), NOW);
    verify(userRepository).consumeActionToken(actionId, NOW);
  }

  @Test
  void invalidActionDoesNotHashOrMutatePassword() {
    // Given
    when(passwordResetCodes.lockActive("learner@example.test", "invalid", NOW))
        .thenThrow(new TrellisException(IdentityErrorCode.ACTION_TOKEN_INVALID, "invalid"));

    // When
    assertThrows(
        TrellisException.class,
        () ->
            resetPasswordUseCase.execute(
                anonymousContext(), "learner@example.test", "invalid", "new-password"));

    // Then
    verifyNoInteractions(passwordHasher, userRepository);
  }
}
