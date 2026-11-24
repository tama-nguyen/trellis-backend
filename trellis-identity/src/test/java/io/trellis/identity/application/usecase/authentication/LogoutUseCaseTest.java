package io.trellis.identity.application.usecase.authentication;

import static io.trellis.identity.IdentityTestFixtures.NOW;
import static io.trellis.identity.IdentityTestFixtures.anonymousContext;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.trellis.identity.application.port.TokenCodec;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.domain.token.TokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseTest {
  @Mock private UserRepository userRepository;

  @Mock private TokenCodec tokenCodec;

  private LogoutUseCase logoutUseCase;

  @BeforeEach
  void setUp() {
    logoutUseCase = new LogoutUseCase(userRepository, tokenCodec);
  }

  @Test
  void revokesRefreshTokenAtRequestTime() {
    // Given
    when(tokenCodec.isType("refresh", TokenType.REFRESH)).thenReturn(true);
    when(tokenCodec.hash("refresh")).thenReturn("refresh-hash");

    // When
    logoutUseCase.execute(anonymousContext(), "refresh");

    // Then
    verify(userRepository).revokeSessionByRefreshToken("refresh-hash", NOW);
  }

  @Test
  void ignoresTokenFromAnotherFamily() {
    // Given
    when(tokenCodec.isType("access", TokenType.REFRESH)).thenReturn(false);

    // When
    logoutUseCase.execute(anonymousContext(), "access");

    // Then
    verify(tokenCodec, never()).hash("access");
    verifyNoInteractions(userRepository);
  }
}
