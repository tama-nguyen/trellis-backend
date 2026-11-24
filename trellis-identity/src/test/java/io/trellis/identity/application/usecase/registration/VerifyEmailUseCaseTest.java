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
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerifyEmailUseCaseTest {
    @Mock
    private UserRepository users;

    @Mock
    private ActionTokens actionTokens;

    private VerifyEmailUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new VerifyEmailUseCase(users, actionTokens);
    }

    @Test
    void verifiesUserAndConsumesActionToken() {
        User user = user(UserStatus.PENDING_VERIFICATION);
        UUID actionId = UUID.randomUUID();
        when(actionTokens.lockActive("raw-action", UserActionType.VERIFY_EMAIL, NOW))
                .thenReturn(new UserActionToken(actionId, user, UserActionType.VERIFY_EMAIL));

        useCase.execute(anonymousContext(), "raw-action");

        verify(users).verifyEmail(user.id(), NOW);
        verify(users).consumeActionToken(actionId, NOW);
    }

    @Test
    void invalidActionDoesNotMutateUser() {
        when(actionTokens.lockActive("invalid", UserActionType.VERIFY_EMAIL, NOW))
                .thenThrow(new TrellisException(
                        io.trellis.identity.api.error.IdentityErrorCode.ACTION_TOKEN_INVALID, "invalid"));

        assertThrows(TrellisException.class, () -> useCase.execute(anonymousContext(), "invalid"));

        verifyNoInteractions(users);
    }
}
