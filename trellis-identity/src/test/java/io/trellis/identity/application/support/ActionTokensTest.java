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
import io.trellis.identity.domain.user.User;
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
class ActionTokensTest {
  @Mock private UserRepository users;

  @Mock private TokenCodec tokens;

  private ActionTokens actionTokens;

  @BeforeEach
  void setUp() {
    actionTokens = new ActionTokens(users, tokens, CLOCK);
  }

  @Test
  void issuesActionTokenAndStoresOnlyHash() {
    User user = user(UserStatus.PENDING_VERIFICATION);
    SecretToken secret = new SecretToken("raw-action", "action-hash");
    when(tokens.issue(TokenType.ACTION)).thenReturn(secret);

    SecretToken result =
        actionTokens.issue(user, UserActionType.VERIFY_EMAIL, Duration.ofMinutes(30), NOW);

    assertEquals(secret, result);
    ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
    verify(users)
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
    when(tokens.isType("refresh", TokenType.ACTION)).thenReturn(false);

    TrellisException failure =
        assertThrows(
            TrellisException.class,
            () -> actionTokens.lockActive("refresh", UserActionType.VERIFY_EMAIL, NOW));

    assertEquals(IdentityErrorCode.ACTION_TOKEN_INVALID, failure.errorCode());
    verify(tokens, never()).hash(any());
    verifyNoInteractions(users);
  }

  @Test
  void returnsLockedActiveAction() {
    UserActionToken action =
        new UserActionToken(
            UUID.randomUUID(), user(UserStatus.PENDING_VERIFICATION), UserActionType.VERIFY_EMAIL);
    when(tokens.isType("raw-action", TokenType.ACTION)).thenReturn(true);
    when(tokens.hash("raw-action")).thenReturn("action-hash");
    when(users.lockActiveActionToken("action-hash", UserActionType.VERIFY_EMAIL, NOW))
        .thenReturn(Optional.of(action));

    UserActionToken result =
        actionTokens.lockActive("raw-action", UserActionType.VERIFY_EMAIL, NOW);

    assertEquals(action, result);
  }

  @Test
  void mapsMissingOrInactiveActionToStableErrorCode() {
    when(tokens.isType("raw-action", TokenType.ACTION)).thenReturn(true);
    when(tokens.hash("raw-action")).thenReturn("action-hash");
    when(users.lockActiveActionToken("action-hash", UserActionType.VERIFY_EMAIL, NOW))
        .thenReturn(Optional.empty());

    TrellisException failure =
        assertThrows(
            TrellisException.class,
            () -> actionTokens.lockActive("raw-action", UserActionType.VERIFY_EMAIL, NOW));

    assertEquals(IdentityErrorCode.ACTION_TOKEN_INVALID, failure.errorCode());
  }
}
