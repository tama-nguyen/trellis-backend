package io.trellis.identity.application.usecase.registration;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.RateLimited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.id.UuidV7;
import io.trellis.identity.IdentityProperties;
import io.trellis.identity.api.error.IdentityErrors;
import io.trellis.identity.application.port.PasswordHasher;
import io.trellis.identity.application.port.TokenCodec.SecretToken;
import io.trellis.identity.application.port.UserMailSender;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.support.ActionTokens;
import io.trellis.identity.domain.model.User;
import io.trellis.identity.domain.model.UserActionType;
import io.trellis.identity.domain.model.UserStatus;
import io.trellis.identity.domain.value.EmailAddress;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a user that cannot sign in yet, and mails the link that will let it. */
@Service
@RequiredArgsConstructor
public class RegisterUseCase {
    private static final int PERMITS = 3;
    private static final String WINDOW = "PT1M";

    private final UserRepository users;
    private final UserMailSender mail;
    private final PasswordHasher passwords;
    private final ActionTokens actionTokens;
    private final IdentityProperties properties;
    private final Clock clock;

    @RateLimited(key = "#email", permits = PERMITS, per = WINDOW)
    @Transactional
    @Observed(name = "identity.register")
    public void execute(RequestContext ctx, String email, String password, String displayName) {
        Instant now = ctx.requestTime();
        User user = new User(
                UuidV7.next(clock),
                EmailAddress.of(email).value(),
                displayName.trim(),
                UserStatus.PENDING_VERIFICATION,
                null,
                now,
                now);
        try {
            users.createUser(user, passwords.hash(password));
        } catch (DuplicateKeyException alreadyTaken) {
            // The unique index is the only safe check: testing first would still race another registration.
            throw IdentityErrors.userAlreadyExists(alreadyTaken);
        }
        SecretToken token =
                actionTokens.issue(user, UserActionType.VERIFY_EMAIL, properties.verifyEmailTokenTtl(), now);
        mail.sendEmailVerification(user.email(), user.displayName(), token.raw());
    }
}
