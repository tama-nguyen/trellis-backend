package io.trellis.identity.application.usecase.password;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.RateLimited;
import io.trellis.common.context.RequestContext;
import io.trellis.identity.IdentityProperties;
import io.trellis.identity.application.port.UserMailSender;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.support.PasswordResetCodes;
import io.trellis.identity.domain.user.EmailAddress;
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserStatus;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mails a reset link, and says nothing about whether it did.
 *
 * <p>An unknown address, a disabled user and a failed send all end the same way, because the response is
 * otherwise an oracle for which addresses are registered.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ForgotPasswordUseCase {
    private static final int PERMITS = 3;
    private static final String WINDOW = "PT1M";

    private final UserRepository users;
    private final UserMailSender mail;
    private final PasswordResetCodes resetCodes;
    private final IdentityProperties properties;

    @RateLimited(key = "#email", permits = PERMITS, per = WINDOW)
    @Transactional
    @Observed(name = "identity.request-password-reset")
    public void execute(RequestContext ctx, String email) {
        users.userByEmail(EmailAddress.of(email)).ifPresent(user -> send(user, ctx.requestTime()));
    }

    private void send(User user, Instant now) {
        if (user.status() == UserStatus.DISABLED) {
            return;
        }
        String code = resetCodes.issue(user, properties.resetPasswordCodeTtl(), now);
        try {
            mail.sendPasswordResetCode(user.email(), user.displayName(), code);
        } catch (MailException undelivered) {
            log.warn("Password reset email delivery failed", undelivered);
        }
    }
}
