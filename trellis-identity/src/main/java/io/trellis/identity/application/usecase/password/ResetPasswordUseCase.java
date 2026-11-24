package io.trellis.identity.application.usecase.password;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.context.RequestContext;
import io.trellis.identity.application.port.PasswordHasher;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.support.ActionTokens;
import io.trellis.identity.domain.action.UserActionToken;
import io.trellis.identity.domain.action.UserActionType;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sets a new password and ends every session, because the old one may be how the password was lost. */
@Service
@RequiredArgsConstructor
public class ResetPasswordUseCase {

    private final UserRepository users;
    private final PasswordHasher passwords;
    private final ActionTokens actionTokens;

    @Transactional
    @Observed(name = "identity.reset-password")
    public void execute(RequestContext ctx, String rawToken, String newPassword) {
        Instant now = ctx.requestTime();
        UserActionToken action = actionTokens.lockActive(rawToken, UserActionType.RESET_PASSWORD, now);
        users.changePassword(action.user().id(), passwords.hash(newPassword), now);
        users.revokeSessions(action.user().id(), now);
        users.consumeActionToken(action.id(), now);
    }
}
