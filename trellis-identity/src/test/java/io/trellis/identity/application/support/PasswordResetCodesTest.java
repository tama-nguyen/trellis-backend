package io.trellis.identity.application.support;

import static io.trellis.identity.IdentityTestFixtures.CLOCK;
import static io.trellis.identity.IdentityTestFixtures.NOW;
import static io.trellis.identity.IdentityTestFixtures.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserStatus;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PasswordResetCodesTest {
    @Mock
    private UserRepository users;

    @Mock
    private TokenCodec tokens;

    private PasswordResetCodes resetCodes;

    @BeforeEach
    void setUp() {
        resetCodes = new PasswordResetCodes(users, tokens, new ReferenceCodeGenerator("PW", new Random(7)), CLOCK);
    }

    @Test
    void issuesHumanReadableCodeAndStoresEmailScopedHash() {
        User user = user(UserStatus.ACTIVE);
        when(tokens.hash(any())).thenReturn("scoped-code-hash");

        String code = resetCodes.issue(user, Duration.ofMinutes(15), NOW);

        assertTrue(code.matches("PW-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}"));
        verify(tokens).hash(user.email() + ":" + code);
        verify(users)
                .createActionToken(
                        any(),
                        org.mockito.ArgumentMatchers.eq(user.id()),
                        org.mockito.ArgumentMatchers.eq(UserActionType.RESET_PASSWORD),
                        org.mockito.ArgumentMatchers.eq("scoped-code-hash"),
                        org.mockito.ArgumentMatchers.eq(NOW.plus(Duration.ofMinutes(15))),
                        org.mockito.ArgumentMatchers.eq(NOW));
    }

    @Test
    void canonicalizesEmailAndCodeBeforeLookup() {
        UserActionToken action =
                new UserActionToken(UUID.randomUUID(), user(UserStatus.ACTIVE), UserActionType.RESET_PASSWORD);
        when(tokens.hash("learner@example.test:PW-1234-5678-9ABC")).thenReturn("scoped-code-hash");
        when(users.lockActiveActionToken("scoped-code-hash", UserActionType.RESET_PASSWORD, NOW))
                .thenReturn(Optional.of(action));

        UserActionToken result = resetCodes.lockActive(" LEARNER@Example.Test ", " pw-1234-5678-9abc ", NOW);

        assertEquals(action, result);
    }

    @Test
    void rejectsMalformedCodeBeforeHashing() {
        TrellisException failure = assertThrows(
                TrellisException.class, () -> resetCodes.lockActive("learner@example.test", "123456", NOW));

        assertEquals(IdentityErrorCode.ACTION_TOKEN_INVALID, failure.errorCode());
        verifyNoInteractions(tokens, users);
    }

    @Test
    void mapsMissingOrInactiveCodeToStableErrorCode() {
        when(tokens.hash("learner@example.test:PW-1234-5678-9ABC")).thenReturn("scoped-code-hash");
        when(users.lockActiveActionToken("scoped-code-hash", UserActionType.RESET_PASSWORD, NOW))
                .thenReturn(Optional.empty());

        TrellisException failure = assertThrows(
                TrellisException.class, () -> resetCodes.lockActive("learner@example.test", "PW-1234-5678-9ABC", NOW));

        assertEquals(IdentityErrorCode.ACTION_TOKEN_INVALID, failure.errorCode());
    }
}
