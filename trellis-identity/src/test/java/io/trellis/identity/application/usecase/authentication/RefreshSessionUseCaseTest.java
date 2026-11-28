package io.trellis.identity.application.usecase.authentication;

import static io.trellis.identity.IdentityTestFixtures.NOW;
import static io.trellis.identity.IdentityTestFixtures.PROPERTIES;
import static io.trellis.identity.IdentityTestFixtures.anonymousContext;
import static io.trellis.identity.IdentityTestFixtures.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.trellis.common.error.TrellisException;
import io.trellis.identity.api.error.IdentityErrorCode;
import io.trellis.identity.application.port.TokenCodec;
import io.trellis.identity.application.port.TokenCodec.SecretToken;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.view.AuthTokens;
import io.trellis.identity.domain.session.UserSession;
import io.trellis.identity.domain.token.TokenType;
import io.trellis.identity.domain.user.UserStatus;
import java.time.Duration;
import java.util.Optional;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshSessionUseCaseTest {
  @Mock private UserRepository userRepository;

  @Mock private TokenCodec tokenCodec;

  private RefreshSessionUseCase refreshSessionUseCase;

  @BeforeEach
  void setUp() {
    refreshSessionUseCase = new RefreshSessionUseCase(userRepository, tokenCodec, PROPERTIES);
  }

  @Test
  void rotatesSecretsWithoutExtendingAbsoluteRefreshExpiry() {
    // Given
    val user = user(UserStatus.ACTIVE);
    val sessionId = 1L;
    val absoluteExpiry = NOW.plus(Duration.ofDays(8));
    when(tokenCodec.isType("old-refresh", TokenType.REFRESH)).thenReturn(true);
    when(tokenCodec.hash("old-refresh")).thenReturn("old-refresh-hash");
    when(userRepository.lockActiveRefreshSession("old-refresh-hash", NOW))
        .thenReturn(Optional.of(new UserSession(sessionId, user, absoluteExpiry)));
    when(tokenCodec.issue(TokenType.ACCESS))
        .thenReturn(new SecretToken("new-access", "new-access-hash"));
    when(tokenCodec.issue(TokenType.REFRESH))
        .thenReturn(new SecretToken("new-refresh", "new-refresh-hash"));

    // When
    val rotatedTokens = refreshSessionUseCase.execute(anonymousContext(), "old-refresh");

    // Then
    assertEquals(new AuthTokens("new-access", "new-refresh"), rotatedTokens);
    verify(userRepository)
        .rotateSession(
            sessionId,
            "new-access-hash",
            "new-refresh-hash",
            NOW.plus(Duration.ofMinutes(15)),
            absoluteExpiry,
            NOW);
  }

  @Test
  void rejectsWrongTokenFamilyBeforeSessionLookup() {
    // Given
    when(tokenCodec.isType("access-token", TokenType.REFRESH)).thenReturn(false);

    // When
    val failure =
        assertThrows(
            TrellisException.class,
            () -> refreshSessionUseCase.execute(anonymousContext(), "access-token"));

    // Then
    assertEquals(IdentityErrorCode.REFRESH_TOKEN_INVALID, failure.errorCode());
    verify(tokenCodec, never()).hash(any());
    verifyNoInteractions(userRepository);
  }

  @Test
  void rejectsUnknownOrInactiveRefreshToken() {
    // Given
    when(tokenCodec.isType("refresh", TokenType.REFRESH)).thenReturn(true);
    when(tokenCodec.hash("refresh")).thenReturn("refresh-hash");
    when(userRepository.lockActiveRefreshSession("refresh-hash", NOW)).thenReturn(Optional.empty());

    // When
    val failure =
        assertThrows(
            TrellisException.class,
            () -> refreshSessionUseCase.execute(anonymousContext(), "refresh"));

    // Then
    assertEquals(IdentityErrorCode.REFRESH_TOKEN_INVALID, failure.errorCode());
    verify(tokenCodec, never()).issue(any());
  }

  @Test
  void rejectsDisabledUserBeforeRotatingSession() {
    // Given
    val user = user(UserStatus.DISABLED);
    when(tokenCodec.isType("refresh", TokenType.REFRESH)).thenReturn(true);
    when(tokenCodec.hash("refresh")).thenReturn("refresh-hash");
    when(userRepository.lockActiveRefreshSession("refresh-hash", NOW))
        .thenReturn(Optional.of(new UserSession(1L, user, NOW.plusSeconds(60))));

    // When
    val failure =
        assertThrows(
            TrellisException.class,
            () -> refreshSessionUseCase.execute(anonymousContext(), "refresh"));

    // Then
    assertEquals(IdentityErrorCode.USER_DISABLED, failure.errorCode());
    verify(tokenCodec, never()).issue(any());
    verify(userRepository, never()).rotateSession(any(), any(), any(), any(), any(), any());
  }

  @Test
  void rejectsPendingUserBeforeRotatingSession() {
    // Given
    val user = user(UserStatus.PENDING_VERIFICATION);
    when(tokenCodec.isType("refresh", TokenType.REFRESH)).thenReturn(true);
    when(tokenCodec.hash("refresh")).thenReturn("refresh-hash");
    when(userRepository.lockActiveRefreshSession("refresh-hash", NOW))
        .thenReturn(Optional.of(new UserSession(1L, user, NOW.plusSeconds(60))));

    // When
    val failure =
        assertThrows(
            TrellisException.class,
            () -> refreshSessionUseCase.execute(anonymousContext(), "refresh"));

    // Then
    assertEquals(IdentityErrorCode.EMAIL_NOT_VERIFIED, failure.errorCode());
    verify(tokenCodec, never()).issue(any());
    verify(userRepository, never()).rotateSession(any(), any(), any(), any(), any(), any());
  }
}
