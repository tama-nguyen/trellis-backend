package io.trellis.identity.application.support;

import static io.trellis.identity.IdentityTestFixtures.CLOCK;
import static io.trellis.identity.IdentityTestFixtures.NOW;
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
import io.trellis.identity.application.port.TokenCodec;
import io.trellis.identity.application.port.TokenCodec.SecretToken;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.domain.action.UserActionToken;
import io.trellis.identity.domain.action.UserActionType;
import io.trellis.identity.domain.token.TokenType;
import io.trellis.identity.domain.user.UserStatus;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActionTokensTest {
  @Mock private UserRepository userRepository;

  @Mock private TokenCodec tokenCodec;

  private ActionTokens actionTokens;

  @BeforeEach
  void setUp() {
    actionTokens = new ActionTokens(userRepository, tokenCodec, CLOCK);
  }

  @Test
  void issuesActionTokenAndStoresOnlyHash() {
    // Given
    val user = user(UserStatus.PENDING_VERIFICATION);
    val secret = new SecretToken("raw-action", "action-hash");
    when(tokenCodec.issue(TokenType.ACTION)).thenReturn(secret);

    // When
    val lockedAction =
        actionTokens.issue(user, UserActionType.VERIFY_EMAIL, Duration.ofMinutes(30), NOW);

    // Then
    assertEquals(secret, lockedAction);
    val id = ArgumentCaptor.forClass(UUID.class);
    verify(userRepository)
        .createActionToken(
            id.capture(),
            eq(user.id()),
            eq(UserActionType.VERIFY_EMAIL),
            eq("action-hash"),
            eq(NOW.plus(Duration.ofMinutes(30))),
            eq(NOW));
    assertEquals(7, id.getValue().version());
  }

  @Test
  void rejectsWrongTokenFamilyBeforeHashing() {
    // Given
    when(tokenCodec.isType("refresh", TokenType.ACTION)).thenReturn(false);

    // When
    val failure =
        assertThrows(
            TrellisException.class,
            () -> actionTokens.lockActive("refresh", UserActionType.VERIFY_EMAIL, NOW));

    // Then
    assertEquals(IdentityErrorCode.ACTION_TOKEN_INVALID, failure.errorCode());
    verify(tokenCodec, never()).hash(any());
    verifyNoInteractions(userRepository);
  }

  @Test
  void returnsLockedActiveAction() {
    // Given
    val action =
        new UserActionToken(
            UUID.randomUUID(), user(UserStatus.PENDING_VERIFICATION), UserActionType.VERIFY_EMAIL);
    when(tokenCodec.isType("raw-action", TokenType.ACTION)).thenReturn(true);
    when(tokenCodec.hash("raw-action")).thenReturn("action-hash");
    when(userRepository.lockActiveActionToken("action-hash", UserActionType.VERIFY_EMAIL, NOW))
        .thenReturn(Optional.of(action));

    // When
    val lockedAction = actionTokens.lockActive("raw-action", UserActionType.VERIFY_EMAIL, NOW);

    // Then
    assertEquals(action, lockedAction);
  }

  @Test
  void mapsMissingOrInactiveActionToStableErrorCode() {
    // Given
    when(tokenCodec.isType("raw-action", TokenType.ACTION)).thenReturn(true);
    when(tokenCodec.hash("raw-action")).thenReturn("action-hash");
    when(userRepository.lockActiveActionToken("action-hash", UserActionType.VERIFY_EMAIL, NOW))
        .thenReturn(Optional.empty());

    // When
    val failure =
        assertThrows(
            TrellisException.class,
            () -> actionTokens.lockActive("raw-action", UserActionType.VERIFY_EMAIL, NOW));

    // Then
    assertEquals(IdentityErrorCode.ACTION_TOKEN_INVALID, failure.errorCode());
  }
}
