package io.trellis.identity.domain.policy;

import io.trellis.identity.domain.model.UserStatus;

/** Which user states may start or continue a session. */
public final class UserSignIn {

    private UserSignIn() {}

    /** Whether {@code status} may sign in; the caller turns a refusal into the right error. */
    public static boolean isAllowed(UserStatus status) {
        return status == UserStatus.ACTIVE;
    }
}
