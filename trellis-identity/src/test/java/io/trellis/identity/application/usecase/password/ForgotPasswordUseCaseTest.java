package io.trellis.identity.application.usecase.password;

import static io.trellis.identity.IdentityTestFixtures.NOW;
import static io.trellis.identity.IdentityTestFixtures.PROPERTIES;
import static io.trellis.identity.IdentityTestFixtures.anonymousContext;
import static io.trellis.identity.IdentityTestFixtures.user;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.trellis.identity.application.port.UserMailSender;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.support.PasswordResetCodes;
import io.trellis.identity.domain.user.EmailAddress;
import io.trellis.identity.domain.user.UserStatus;
import java.util.Optional;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

@ExtendWith(MockitoExtension.class)
class ForgotPasswordUseCaseTest {
  @Mock private UserRepository userRepository;

  @Mock private UserMailSender userMailSender;

  @Mock private PasswordResetCodes passwordResetCodes;

  private ForgotPasswordUseCase forgotPasswordUseCase;

  @BeforeEach
  void setUp() {
    forgotPasswordUseCase =
        new ForgotPasswordUseCase(userRepository, userMailSender, passwordResetCodes, PROPERTIES);
  }

  @Test
  void unknownEmailHasNoObservableSideEffect() {
    // Given
    when(userRepository.userByEmail(new EmailAddress("missing@example.test")))
        .thenReturn(Optional.empty());

    // When
    forgotPasswordUseCase.execute(anonymousContext(), "  MISSING@Example.Test ");

    // Then
    verifyNoInteractions(passwordResetCodes, userMailSender);
  }

  @Test
  void disabledUserReceivesNoResetToken() {
    // Given
    val user = user(UserStatus.DISABLED);
    when(userRepository.userByEmail(new EmailAddress(user.email()))).thenReturn(Optional.of(user));

    // When
    forgotPasswordUseCase.execute(anonymousContext(), user.email());

    // Then
    verifyNoInteractions(passwordResetCodes, userMailSender);
  }

  @Test
  void sendsPasswordResetForEligibleUser() {
    // Given
    val user = user(UserStatus.ACTIVE);
    when(userRepository.userByEmail(new EmailAddress(user.email()))).thenReturn(Optional.of(user));
    when(passwordResetCodes.issue(user, PROPERTIES.resetPasswordCodeTtl(), NOW))
        .thenReturn("PW-1234-5678-9ABC");

    // When
    forgotPasswordUseCase.execute(anonymousContext(), user.email());

    // Then
    verify(userMailSender)
        .sendPasswordResetCode(user.email(), user.displayName(), "PW-1234-5678-9ABC");
  }

  @Test
  void propagatesMailEnqueueFailure() {
    // Given
    val user = user(UserStatus.ACTIVE);
    when(userRepository.userByEmail(new EmailAddress(user.email()))).thenReturn(Optional.of(user));
    when(passwordResetCodes.issue(user, PROPERTIES.resetPasswordCodeTtl(), NOW))
        .thenReturn("PW-1234-5678-9ABC");
    val unavailable = new MailSendException("mail unavailable");
    doThrow(unavailable)
        .when(userMailSender)
        .sendPasswordResetCode(user.email(), user.displayName(), "PW-1234-5678-9ABC");

    // When
    val failure =
        assertThrows(
            MailSendException.class,
            () -> forgotPasswordUseCase.execute(anonymousContext(), user.email()));

    // Then
    assertSame(unavailable, failure);
  }
}
