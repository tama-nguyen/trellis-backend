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
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResetPasswordUseCaseTest {
    @Mock
    private UserRepository users;

    @Mock
    private PasswordHasher passwords;

    @Mock
    private PasswordResetCodes resetCodes;

    private ResetPasswordUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ResetPasswordUseCase(users, passwords, resetCodes);
    }

    @Test
    void changesPasswordRevokesSessionsAndConsumesAction() {
        User user = user(UserStatus.ACTIVE);
        UUID actionId = UUID.randomUUID();
        when(resetCodes.lockActive(user.email(), "PW-1234-5678-9ABC", NOW))
                .thenReturn(new UserActionToken(actionId, user, UserActionType.RESET_PASSWORD));
        when(passwords.hash("new-password")).thenReturn("new-password-hash");

        useCase.execute(anonymousContext(), user.email(), "PW-1234-5678-9ABC", "new-password");

        verify(users).changePassword(user.id(), "new-password-hash", NOW);
        verify(users).revokeSessions(user.id(), NOW);
        verify(users).consumeActionToken(actionId, NOW);
    }

    @Test
    void invalidActionDoesNotHashOrMutatePassword() {
        when(resetCodes.lockActive("learner@example.test", "invalid", NOW))
                .thenThrow(new TrellisException(IdentityErrorCode.ACTION_TOKEN_INVALID, "invalid"));

        assertThrows(
                TrellisException.class,
                () -> useCase.execute(anonymousContext(), "learner@example.test", "invalid", "new-password"));

        verifyNoInteractions(passwords, users);
    }
}
