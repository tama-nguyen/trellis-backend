package io.trellis.identity.application.usecase.password;

import static io.trellis.identity.IdentityTestFixtures.NOW;
import static io.trellis.identity.IdentityTestFixtures.PROPERTIES;
import static io.trellis.identity.IdentityTestFixtures.anonymousContext;
import static io.trellis.identity.IdentityTestFixtures.user;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.trellis.identity.application.port.UserMailSender;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.support.PasswordResetCodes;
import io.trellis.identity.domain.user.EmailAddress;
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserStatus;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

@ExtendWith(MockitoExtension.class)
class ForgotPasswordUseCaseTest {
    @Mock
    private UserRepository users;

    @Mock
    private UserMailSender mail;

    @Mock
    private PasswordResetCodes resetCodes;

    private ForgotPasswordUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ForgotPasswordUseCase(users, mail, resetCodes, PROPERTIES);
    }

    @Test
    void unknownEmailHasNoObservableSideEffect() {
        when(users.userByEmail(new EmailAddress("missing@example.test"))).thenReturn(Optional.empty());

        useCase.execute(anonymousContext(), "  MISSING@Example.Test ");

        verifyNoInteractions(resetCodes, mail);
    }

    @Test
    void disabledUserReceivesNoResetToken() {
        User user = user(UserStatus.DISABLED);
        when(users.userByEmail(new EmailAddress(user.email()))).thenReturn(Optional.of(user));

        useCase.execute(anonymousContext(), user.email());

        verifyNoInteractions(resetCodes, mail);
    }

    @Test
    void sendsPasswordResetForEligibleUser() {
        User user = user(UserStatus.ACTIVE);
        when(users.userByEmail(new EmailAddress(user.email()))).thenReturn(Optional.of(user));
        when(resetCodes.issue(user, PROPERTIES.resetPasswordCodeTtl(), NOW)).thenReturn("PW-1234-5678-9ABC");

        useCase.execute(anonymousContext(), user.email());

        verify(mail).sendPasswordResetCode(user.email(), user.displayName(), "PW-1234-5678-9ABC");
    }

    @Test
    void mailFailureIsNotExposedToCaller() {
        User user = user(UserStatus.ACTIVE);
        when(users.userByEmail(new EmailAddress(user.email()))).thenReturn(Optional.of(user));
        when(resetCodes.issue(user, PROPERTIES.resetPasswordCodeTtl(), NOW)).thenReturn("PW-1234-5678-9ABC");
        org.mockito.Mockito.doThrow(new MailSendException("mail unavailable"))
                .when(mail)
                .sendPasswordResetCode(user.email(), user.displayName(), "PW-1234-5678-9ABC");

        assertDoesNotThrow(() -> useCase.execute(anonymousContext(), user.email()));
    }
}
