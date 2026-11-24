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
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserCredentials;
import io.trellis.identity.domain.user.UserStatus;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {
    @Mock
    private UserRepository users;

    @Mock
    private PasswordHasher passwords;

    @Mock
    private TokenCodec tokens;

    private LoginUseCase useCase;

    @BeforeEach
    void setUp() {
        when(passwords.hash("not-a-real-user-password")).thenReturn("absent-user-hash");
        useCase = new LoginUseCase(users, passwords, tokens, PROPERTIES, CLOCK);
    }

    @Test
    void opensSessionForActiveUser() {
        User user = user(UserStatus.ACTIVE);
        when(users.credentialsByEmail(new EmailAddress("learner@example.test")))
                .thenReturn(Optional.of(new UserCredentials(user, "stored-password-hash")));
        when(passwords.matches("correct-password", "stored-password-hash")).thenReturn(true);
        when(tokens.issue(TokenType.ACCESS)).thenReturn(new SecretToken("raw-access", "access-hash"));
        when(tokens.issue(TokenType.REFRESH)).thenReturn(new SecretToken("raw-refresh", "refresh-hash"));

        AuthTokens result = useCase.execute(anonymousContext(), "  LEARNER@Example.Test ", "correct-password");

        assertEquals(new AuthTokens("raw-access", "raw-refresh"), result);
        ArgumentCaptor<UUID> sessionId = ArgumentCaptor.forClass(UUID.class);
        verify(users)
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
        when(users.credentialsByEmail(new EmailAddress("missing@example.test"))).thenReturn(Optional.empty());

        TrellisException failure = assertThrows(
                TrellisException.class, () -> useCase.execute(anonymousContext(), "missing@example.test", "guess"));

        assertEquals(IdentityErrorCode.INVALID_CREDENTIALS, failure.errorCode());
        verify(passwords).matches("guess", "absent-user-hash");
        verify(users, never()).createSession(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsWrongPasswordWithoutOpeningSession() {
        User user = user(UserStatus.ACTIVE);
        when(users.credentialsByEmail(new EmailAddress(user.email())))
                .thenReturn(Optional.of(new UserCredentials(user, "stored-password-hash")));

        TrellisException failure = assertThrows(
                TrellisException.class, () -> useCase.execute(anonymousContext(), user.email(), "wrong-password"));

        assertEquals(IdentityErrorCode.INVALID_CREDENTIALS, failure.errorCode());
        verifyNoInteractions(tokens);
        verify(users, never()).createSession(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsPendingUserBeforeIssuingTokens() {
        User user = user(UserStatus.PENDING_VERIFICATION);
        when(users.credentialsByEmail(new EmailAddress(user.email())))
                .thenReturn(Optional.of(new UserCredentials(user, "stored-password-hash")));
        when(passwords.matches("correct-password", "stored-password-hash")).thenReturn(true);

        TrellisException failure = assertThrows(
                TrellisException.class, () -> useCase.execute(anonymousContext(), user.email(), "correct-password"));

        assertEquals(IdentityErrorCode.EMAIL_NOT_VERIFIED, failure.errorCode());
        verifyNoInteractions(tokens);
        verify(users, never()).createSession(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsDisabledUserBeforeIssuingTokens() {
        User user = user(UserStatus.DISABLED);
        when(users.credentialsByEmail(new EmailAddress(user.email())))
                .thenReturn(Optional.of(new UserCredentials(user, "stored-password-hash")));
        when(passwords.matches("correct-password", "stored-password-hash")).thenReturn(true);

        TrellisException failure = assertThrows(
                TrellisException.class, () -> useCase.execute(anonymousContext(), user.email(), "correct-password"));

        assertEquals(IdentityErrorCode.USER_DISABLED, failure.errorCode());
        verifyNoInteractions(tokens);
        verify(users, never()).createSession(any(), any(), any(), any(), any(), any(), any());
    }
}
