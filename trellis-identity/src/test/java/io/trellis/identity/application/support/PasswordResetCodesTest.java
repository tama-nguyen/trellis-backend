package io.trellis.identity.application.support;

import static io.trellis.identity.IdentityTestFixtures.NOW;
import static io.trellis.identity.IdentityTestFixtures.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.trellis.common.error.TrellisException;
import io.trellis.common.id.ReferenceCodeGenerator;
import io.trellis.identity.api.error.IdentityErrorCode;
import io.trellis.identity.application.port.TokenCodec;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.domain.action.UserActionToken;
import io.trellis.identity.domain.action.UserActionType;
import io.trellis.identity.domain.user.UserStatus;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PasswordResetCodesTest {
  @Mock private UserRepository userRepository;

  @Mock private TokenCodec tokenCodec;

  private PasswordResetCodes passwordResetCodes;

  @BeforeEach
  void setUp() {
    passwordResetCodes =
        new PasswordResetCodes(
            userRepository, tokenCodec, new ReferenceCodeGenerator("PW", new Random(7)));
  }

  @Test
  void issuesHumanReadableCodeAndStoresEmailScopedHash() {
    // Given
    val user = user(UserStatus.ACTIVE);
    when(tokenCodec.hash(any())).thenReturn("scoped-code-hash");

    // When
    val code = passwordResetCodes.issue(user, Duration.ofMinutes(15), NOW);

    // Then
    assertTrue(
        code.matches("PW-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}"));
    verify(tokenCodec).hash(user.email() + ":" + code);
    verify(userRepository)
        .createActionToken(
            eq(user.id()),
            eq(UserActionType.RESET_PASSWORD),
            eq("scoped-code-hash"),
            eq(NOW.plus(Duration.ofMinutes(15))),
            eq(NOW));
  }

  @Test
  void canonicalizesEmailAndCodeBeforeLookup() {
    // Given
    val action = new UserActionToken(1L, user(UserStatus.ACTIVE), UserActionType.RESET_PASSWORD);
    when(tokenCodec.hash("learner@example.test:PW-1234-5678-9ABC")).thenReturn("scoped-code-hash");
    when(userRepository.lockActiveActionToken(
            "scoped-code-hash", UserActionType.RESET_PASSWORD, NOW))
        .thenReturn(Optional.of(action));

    // When
    val lockedAction =
        passwordResetCodes.lockActive(" LEARNER@Example.Test ", " pw-1234-5678-9abc ", NOW);

    // Then
    assertEquals(action, lockedAction);
  }

  @Test
  void rejectsMalformedCodeBeforeHashing() {
    // When
    val failure =
        assertThrows(
            TrellisException.class,
            () -> passwordResetCodes.lockActive("learner@example.test", "123456", NOW));

    // Then
    assertEquals(IdentityErrorCode.ACTION_TOKEN_INVALID, failure.errorCode());
    verifyNoInteractions(tokenCodec, userRepository);
  }

  @Test
  void mapsMissingOrInactiveCodeToStableErrorCode() {
    // Given
    when(tokenCodec.hash("learner@example.test:PW-1234-5678-9ABC")).thenReturn("scoped-code-hash");
    when(userRepository.lockActiveActionToken(
            "scoped-code-hash", UserActionType.RESET_PASSWORD, NOW))
        .thenReturn(Optional.empty());

    // When
    val failure =
        assertThrows(
            TrellisException.class,
            () -> passwordResetCodes.lockActive("learner@example.test", "PW-1234-5678-9ABC", NOW));

    // Then
    assertEquals(IdentityErrorCode.ACTION_TOKEN_INVALID, failure.errorCode());
  }
}
