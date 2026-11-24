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
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshSessionUseCaseTest {
    @Mock
    private UserRepository users;

    @Mock
    private TokenCodec tokens;

    private RefreshSessionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RefreshSessionUseCase(users, tokens, PROPERTIES);
    }

    @Test
    void rotatesSecretsWithoutExtendingAbsoluteRefreshExpiry() {
        User user = user(UserStatus.ACTIVE);
        UUID sessionId = UUID.randomUUID();
        Instant absoluteExpiry = NOW.plus(Duration.ofDays(8));
        when(tokens.isType("old-refresh", TokenType.REFRESH)).thenReturn(true);
        when(tokens.hash("old-refresh")).thenReturn("old-refresh-hash");
        when(users.lockActiveRefreshSession("old-refresh-hash", NOW))
                .thenReturn(Optional.of(new UserSession(sessionId, user, absoluteExpiry)));
        when(tokens.issue(TokenType.ACCESS)).thenReturn(new SecretToken("new-access", "new-access-hash"));
        when(tokens.issue(TokenType.REFRESH)).thenReturn(new SecretToken("new-refresh", "new-refresh-hash"));

        AuthTokens result = useCase.execute(anonymousContext(), "old-refresh");

        assertEquals(new AuthTokens("new-access", "new-refresh"), result);
        verify(users)
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
        when(tokens.isType("access-token", TokenType.REFRESH)).thenReturn(false);

        TrellisException failure =
                assertThrows(TrellisException.class, () -> useCase.execute(anonymousContext(), "access-token"));

        assertEquals(IdentityErrorCode.REFRESH_TOKEN_INVALID, failure.errorCode());
        verify(tokens, never()).hash(any());
        verifyNoInteractions(users);
    }

    @Test
    void rejectsUnknownOrInactiveRefreshToken() {
        when(tokens.isType("refresh", TokenType.REFRESH)).thenReturn(true);
        when(tokens.hash("refresh")).thenReturn("refresh-hash");
        when(users.lockActiveRefreshSession("refresh-hash", NOW)).thenReturn(Optional.empty());

        TrellisException failure =
                assertThrows(TrellisException.class, () -> useCase.execute(anonymousContext(), "refresh"));

        assertEquals(IdentityErrorCode.REFRESH_TOKEN_INVALID, failure.errorCode());
        verify(tokens, never()).issue(any());
    }

    @Test
    void rejectsDisabledUserBeforeRotatingSession() {
        User user = user(UserStatus.DISABLED);
        when(tokens.isType("refresh", TokenType.REFRESH)).thenReturn(true);
        when(tokens.hash("refresh")).thenReturn("refresh-hash");
        when(users.lockActiveRefreshSession("refresh-hash", NOW))
                .thenReturn(Optional.of(new UserSession(UUID.randomUUID(), user, NOW.plusSeconds(60))));

        TrellisException failure =
                assertThrows(TrellisException.class, () -> useCase.execute(anonymousContext(), "refresh"));

        assertEquals(IdentityErrorCode.USER_DISABLED, failure.errorCode());
        verify(tokens, never()).issue(any());
        verify(users, never()).rotateSession(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsPendingUserBeforeRotatingSession() {
        User user = user(UserStatus.PENDING_VERIFICATION);
        when(tokens.isType("refresh", TokenType.REFRESH)).thenReturn(true);
        when(tokens.hash("refresh")).thenReturn("refresh-hash");
        when(users.lockActiveRefreshSession("refresh-hash", NOW))
                .thenReturn(Optional.of(new UserSession(UUID.randomUUID(), user, NOW.plusSeconds(60))));

        TrellisException failure =
                assertThrows(TrellisException.class, () -> useCase.execute(anonymousContext(), "refresh"));

        assertEquals(IdentityErrorCode.EMAIL_NOT_VERIFIED, failure.errorCode());
        verify(tokens, never()).issue(any());
        verify(users, never()).rotateSession(any(), any(), any(), any(), any(), any());
    }
}
