package io.trellis.identity.application.usecase.registration;

import static io.trellis.identity.IdentityTestFixtures.NOW;
import static io.trellis.identity.IdentityTestFixtures.PROPERTIES;
import static io.trellis.identity.IdentityTestFixtures.anonymousContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import io.trellis.identity.domain.user.NewUser;
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserStatus;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class RegisterUseCaseTest {
  @Mock private UserRepository userRepository;

  @Mock private UserMailSender userMailSender;

  @Mock private PasswordHasher passwordHasher;

  @Mock private ActionTokens actionTokens;

  private RegisterUseCase registerUseCase;

  @BeforeEach
  void setUp() {
    registerUseCase =
        new RegisterUseCase(
            userRepository, userMailSender, passwordHasher, actionTokens, PROPERTIES);
  }

  @Test
  void createsPendingUserAndSendsVerificationToken() {
    // Given
    val persistedUser =
        new User(
            42L, "new@example.test", "New User", UserStatus.PENDING_VERIFICATION, null, NOW, NOW);
    when(userRepository.createUser(any(), eq("password-hash"))).thenReturn(persistedUser);
    when(passwordHasher.hash("password")).thenReturn("password-hash");
    when(actionTokens.issue(
            any(), eq(UserActionType.VERIFY_EMAIL), eq(PROPERTIES.verifyEmailTokenTtl()), eq(NOW)))
        .thenReturn(new SecretToken("raw-verification", "verification-hash"));

    // When
    val userId =
        registerUseCase.execute(
            anonymousContext(), "  NEW@Example.Test ", "password", "  New User  ");

    val createdUser = ArgumentCaptor.forClass(NewUser.class);

    // Then
    verify(userRepository).createUser(createdUser.capture(), eq("password-hash"));
    val user = createdUser.getValue();
    assertEquals("new@example.test", user.email());
    assertEquals("New User", user.displayName());
    assertEquals(UserStatus.PENDING_VERIFICATION, user.status());
    assertEquals(NOW, user.createdAt());
    assertEquals(persistedUser.id(), userId);
    verify(actionTokens)
        .issue(persistedUser, UserActionType.VERIFY_EMAIL, PROPERTIES.verifyEmailTokenTtl(), NOW);
    verify(userMailSender)
        .sendEmailVerification("new@example.test", "New User", "raw-verification");
  }

  @Test
  void mapsUniqueEmailRaceWithoutIssuingTokenOrMail() {
    // Given
    when(passwordHasher.hash("password")).thenReturn("password-hash");
    val duplicate = new DuplicateKeyException("users_email_key");
    doThrow(duplicate).when(userRepository).createUser(any(), eq("password-hash"));

    // When
    val failure =
        assertThrows(
            TrellisException.class,
            () ->
                registerUseCase.execute(
                    anonymousContext(), "existing@example.test", "password", "Existing"));

    // Then
    assertEquals(IdentityErrorCode.USER_ALREADY_EXISTS, failure.errorCode());
    assertEquals(duplicate, failure.getCause());
    verifyNoInteractions(actionTokens, userMailSender);
  }
}
