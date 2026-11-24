package io.trellis.identity.application.usecase.authentication;

import static io.trellis.identity.IdentityTestFixtures.CLOCK;
import static io.trellis.identity.IdentityTestFixtures.NOW;
import static io.trellis.identity.IdentityTestFixtures.PROPERTIES;
import static io.trellis.identity.IdentityTestFixtures.anonymousContext;
import static io.trellis.identity.IdentityTestFixtures.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.trellis.common.error.TrellisException;
import io.trellis.identity.api.error.IdentityErrorCode;
import io.trellis.identity.application.port.PasswordHasher;
import io.trellis.identity.application.port.TokenCodec;
import io.trellis.identity.application.port.TokenCodec.SecretToken;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.view.AuthTokens;
import io.trellis.identity.domain.token.TokenType;
import io.trellis.identity.domain.user.EmailAddress;
import io.trellis.identity.domain.user.UserCredentials;
import io.trellis.identity.domain.user.UserStatus;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {
  @Mock private UserRepository userRepository;

  @Mock private PasswordHasher passwordHasher;

  @Mock private TokenCodec tokenCodec;

  private LoginUseCase loginUseCase;

  @BeforeEach
  void setUp() {
    when(passwordHasher.hash("not-a-real-user-password")).thenReturn("absent-user-hash");
    loginUseCase = new LoginUseCase(userRepository, passwordHasher, tokenCodec, PROPERTIES, CLOCK);
  }

  @Test
  void opensSessionForActiveUser() {
    // Given
    val user = user(UserStatus.ACTIVE);
    when(userRepository.credentialsByEmail(new EmailAddress("learner@example.test")))
        .thenReturn(Optional.of(new UserCredentials(user, "stored-password-hash")));
    when(passwordHasher.matches("correct-password", "stored-password-hash")).thenReturn(true);
    when(tokenCodec.issue(TokenType.ACCESS))
        .thenReturn(new SecretToken("raw-access", "access-hash"));
    when(tokenCodec.issue(TokenType.REFRESH))
        .thenReturn(new SecretToken("raw-refresh", "refresh-hash"));

    // When
    val authenticatedTokens =
        loginUseCase.execute(anonymousContext(), "  LEARNER@Example.Test ", "correct-password");

    // Then
    assertEquals(new AuthTokens("raw-access", "raw-refresh"), authenticatedTokens);
    val sessionId = ArgumentCaptor.forClass(UUID.class);
    verify(userRepository)
        .createSession(
            sessionId.capture(),
            eq(user.id()),
            eq("access-hash"),
            eq("refresh-hash"),
            eq(NOW.plus(Duration.ofMinutes(15))),
            eq(NOW.plus(Duration.ofDays(30))),
            eq(NOW));
    assertEquals(7, sessionId.getValue().version());
  }

  @Test
  void rejectsUnknownEmailAfterDummyHashCheck() {
    // Given
    when(userRepository.credentialsByEmail(new EmailAddress("missing@example.test")))
        .thenReturn(Optional.empty());

    // When
    val failure =
        assertThrows(
            TrellisException.class,
            () -> loginUseCase.execute(anonymousContext(), "missing@example.test", "guess"));

    // Then
    assertEquals(IdentityErrorCode.INVALID_CREDENTIALS, failure.errorCode());
    verify(passwordHasher).matches("guess", "absent-user-hash");
    verify(userRepository, never()).createSession(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void rejectsWrongPasswordWithoutOpeningSession() {
    // Given
    val user = user(UserStatus.ACTIVE);
    when(userRepository.credentialsByEmail(new EmailAddress(user.email())))
        .thenReturn(Optional.of(new UserCredentials(user, "stored-password-hash")));

    // When
    val failure =
        assertThrows(
            TrellisException.class,
            () -> loginUseCase.execute(anonymousContext(), user.email(), "wrong-password"));

    // Then
    assertEquals(IdentityErrorCode.INVALID_CREDENTIALS, failure.errorCode());
    verifyNoInteractions(tokenCodec);
    verify(userRepository, never()).createSession(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void rejectsPendingUserBeforeIssuingTokens() {
    // Given
    val user = user(UserStatus.PENDING_VERIFICATION);
    when(userRepository.credentialsByEmail(new EmailAddress(user.email())))
        .thenReturn(Optional.of(new UserCredentials(user, "stored-password-hash")));
    when(passwordHasher.matches("correct-password", "stored-password-hash")).thenReturn(true);

    // When
    val failure =
        assertThrows(
            TrellisException.class,
            () -> loginUseCase.execute(anonymousContext(), user.email(), "correct-password"));

    // Then
    assertEquals(IdentityErrorCode.EMAIL_NOT_VERIFIED, failure.errorCode());
    verifyNoInteractions(tokenCodec);
    verify(userRepository, never()).createSession(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void rejectsDisabledUserBeforeIssuingTokens() {
    // Given
    val user = user(UserStatus.DISABLED);
    when(userRepository.credentialsByEmail(new EmailAddress(user.email())))
        .thenReturn(Optional.of(new UserCredentials(user, "stored-password-hash")));
    when(passwordHasher.matches("correct-password", "stored-password-hash")).thenReturn(true);

    // When
    val failure =
        assertThrows(
            TrellisException.class,
            () -> loginUseCase.execute(anonymousContext(), user.email(), "correct-password"));

    // Then
    assertEquals(IdentityErrorCode.USER_DISABLED, failure.errorCode());
    verifyNoInteractions(tokenCodec);
    verify(userRepository, never()).createSession(any(), any(), any(), any(), any(), any(), any());
  }
}
