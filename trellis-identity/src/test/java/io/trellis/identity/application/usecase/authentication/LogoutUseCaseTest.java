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
  @Mock private UserRepository users;

  @Mock private TokenCodec tokens;

  private LogoutUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new LogoutUseCase(users, tokens);
  }

  @Test
  void revokesRefreshTokenAtRequestTime() {
    when(tokens.isType("refresh", TokenType.REFRESH)).thenReturn(true);
    when(tokens.hash("refresh")).thenReturn("refresh-hash");

    useCase.execute(anonymousContext(), "refresh");

    verify(users).revokeSessionByRefreshToken("refresh-hash", NOW);
  }

  @Test
  void ignoresTokenFromAnotherFamily() {
    when(tokens.isType("access", TokenType.REFRESH)).thenReturn(false);

    useCase.execute(anonymousContext(), "access");

    verify(tokens, never()).hash("access");
    verifyNoInteractions(users);
  }
}
