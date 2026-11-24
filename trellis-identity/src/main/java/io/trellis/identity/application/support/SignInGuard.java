package io.trellis.identity.application.support;

import io.trellis.identity.api.error.IdentityErrors;
import io.trellis.identity.domain.model.User;
import io.trellis.identity.domain.policy.UserSignIn;

/**
 * Turns the domain's sign-in rule into the right refusal. Signing in and refreshing a session both have to
 * apply it, and they must refuse identically - a difference between them would tell an attacker which state
 * an address is in.
 */
public final class SignInGuard {

    private SignInGuard() {}

    public static void require(User user) {
        if (UserSignIn.isAllowed(user.status())) {
            return;
        }
        throw switch (user.status()) {
            case PENDING_VERIFICATION -> IdentityErrors.emailNotVerified();
            case DISABLED -> IdentityErrors.userDisabled();
            case ACTIVE -> IdentityErrors.invalidCredentials();
        };
    }
}
