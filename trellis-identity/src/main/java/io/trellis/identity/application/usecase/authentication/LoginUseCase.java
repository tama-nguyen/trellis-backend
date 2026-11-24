package io.trellis.identity.application.usecase.authentication;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.RateLimited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.id.UuidV7;
import io.trellis.identity.IdentityProperties;
import io.trellis.identity.api.error.IdentityErrors;
import io.trellis.identity.application.port.PasswordHasher;
import io.trellis.identity.application.port.TokenCodec;
import io.trellis.identity.application.port.TokenCodec.SecretToken;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.support.SignInGuard;
import io.trellis.identity.application.view.AuthTokens;
import io.trellis.identity.domain.token.TokenType;
import io.trellis.identity.domain.user.EmailAddress;
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserCredentials;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exchanges an email and password for a session. */
@Service
public class LoginUseCase {
    private static final int PERMITS = 5;
    private static final String WINDOW = "PT1M";
    /**
     * Verified when no user matches, so a wrong email costs the same time as a wrong password. Without it,
     * response timing tells an attacker which addresses are registered.
     */
    private static final String ABSENT_USER_PASSWORD = "not-a-real-user-password";

    private final UserRepository users;
    private final PasswordHasher passwords;
    private final TokenCodec tokens;
    private final IdentityProperties properties;
    private final Clock clock;
    private final String absentUserHash;

    public LoginUseCase(
            UserRepository users,
            PasswordHasher passwords,
            TokenCodec tokens,
            IdentityProperties properties,
            Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
        this.absentUserHash = passwords.hash(ABSENT_USER_PASSWORD);
    }

    @RateLimited(key = "#email", permits = PERMITS, per = WINDOW)
    @Transactional
    @Observed(name = "identity.sign-in")
    public AuthTokens execute(RequestContext ctx, String email, String password) {
        Optional<UserCredentials> found = users.credentialsByEmail(EmailAddress.of(email));
        String passwordHash = found.map(UserCredentials::passwordHash).orElse(absentUserHash);
        if (!passwords.matches(password, passwordHash) || found.isEmpty()) {
            throw IdentityErrors.invalidCredentials();
        }
        User user = found.orElseThrow().user();
        SignInGuard.require(user);
        return openSession(user, ctx.requestTime());
    }

    private AuthTokens openSession(User user, Instant now) {
        SecretToken access = tokens.issue(TokenType.ACCESS);
        SecretToken refresh = tokens.issue(TokenType.REFRESH);
        users.createSession(
                UuidV7.next(clock),
                user.id(),
                access.hash(),
                refresh.hash(),
                now.plus(properties.accessTokenTtl()),
                now.plus(properties.refreshTokenTtl()),
                now);
        return new AuthTokens(access.raw(), refresh.raw());
    }
}
