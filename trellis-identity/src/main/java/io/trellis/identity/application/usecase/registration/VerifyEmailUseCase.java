package io.trellis.identity.application.usecase.registration;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.context.RequestContext;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.support.ActionTokens;
import io.trellis.identity.domain.model.UserActionToken;
import io.trellis.identity.domain.model.UserActionType;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Turns a pending registration into one that can sign in. */
@Service
@RequiredArgsConstructor
public class VerifyEmailUseCase {

    private final UserRepository users;
    private final ActionTokens actionTokens;

    @Transactional
    @Observed(name = "identity.verify-email")
    public void execute(RequestContext ctx, String rawToken) {
        Instant now = ctx.requestTime();
        UserActionToken action = actionTokens.lockActive(rawToken, UserActionType.VERIFY_EMAIL, now);
        users.verifyEmail(action.user().id(), now);
        users.consumeActionToken(action.id(), now);
    }
}
