package io.trellis.identity.application.usecase.registration;

import static io.trellis.identity.IdentityTestFixtures.CLOCK;
import static io.trellis.identity.IdentityTestFixtures.NOW;
import static io.trellis.identity.IdentityTestFixtures.PROPERTIES;
import static io.trellis.identity.IdentityTestFixtures.anonymousContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.trellis.common.error.TrellisException;
import io.trellis.identity.api.error.IdentityErrorCode;
import io.trellis.identity.application.port.PasswordHasher;
import io.trellis.identity.application.port.TokenCodec.SecretToken;
import io.trellis.identity.application.port.UserMailSender;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.support.ActionTokens;
import io.trellis.identity.domain.action.UserActionType;
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class RegisterUseCaseTest {
  @Mock private UserRepository users;

  @Mock private UserMailSender mail;

  @Mock private PasswordHasher passwords;

  @Mock private ActionTokens actionTokens;

  private RegisterUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new RegisterUseCase(users, mail, passwords, actionTokens, PROPERTIES, CLOCK);
  }

  @Test
  void createsPendingUserAndSendsVerificationToken() {
    when(passwords.hash("password")).thenReturn("password-hash");
    when(actionTokens.issue(
            any(), eq(UserActionType.VERIFY_EMAIL), eq(PROPERTIES.verifyEmailTokenTtl()), eq(NOW)))
        .thenReturn(new SecretToken("raw-verification", "verification-hash"));

    useCase.execute(anonymousContext(), "  NEW@Example.Test ", "password", "  New User  ");

    ArgumentCaptor<User> createdUser = ArgumentCaptor.forClass(User.class);
    verify(users).createUser(createdUser.capture(), eq("password-hash"));
    User user = createdUser.getValue();
    assertEquals("new@example.test", user.email());
    assertEquals("New User", user.displayName());
    assertEquals(UserStatus.PENDING_VERIFICATION, user.status());
    assertEquals(NOW, user.createdAt());
    assertEquals(7, user.id().version());
    verify(actionTokens)
        .issue(user, UserActionType.VERIFY_EMAIL, PROPERTIES.verifyEmailTokenTtl(), NOW);
    verify(mail).sendEmailVerification("new@example.test", "New User", "raw-verification");
  }

  @Test
  void mapsUniqueEmailRaceWithoutIssuingTokenOrMail() {
    when(passwords.hash("password")).thenReturn("password-hash");
    DuplicateKeyException duplicate = new DuplicateKeyException("users_email_key");
    org.mockito.Mockito.doThrow(duplicate).when(users).createUser(any(), eq("password-hash"));

    TrellisException failure =
        assertThrows(
            TrellisException.class,
            () ->
                useCase.execute(
                    anonymousContext(), "existing@example.test", "password", "Existing"));

    assertEquals(IdentityErrorCode.USER_ALREADY_EXISTS, failure.errorCode());
    assertEquals(duplicate, failure.getCause());
    verifyNoInteractions(actionTokens, mail);
  }
}
