package io.trellis.identity.api.error;

import io.trellis.common.error.TrellisException;

/**
 * Factories for this module's failures. Messages stay deliberately vague about whether an user exists: sign-in
 * and password-reset responses must not become an user-enumeration oracle.
 */
public final class IdentityErrors {

    private IdentityErrors() {}

    public static TrellisException invalidCredentials() {
        return new TrellisException(IdentityErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect");
    }

    public static TrellisException emailNotVerified() {
        return new TrellisException(IdentityErrorCode.EMAIL_NOT_VERIFIED, "Verify the user email before signing in");
    }

    public static TrellisException userDisabled() {
        return new TrellisException(IdentityErrorCode.USER_DISABLED, "This user is disabled");
    }

    public static TrellisException userAlreadyExists(Throwable cause) {
        return new TrellisException(IdentityErrorCode.USER_ALREADY_EXISTS, "An user already uses this email", cause);
    }

    public static TrellisException refreshTokenInvalid() {
        return new TrellisException(IdentityErrorCode.REFRESH_TOKEN_INVALID, "Refresh token is invalid or expired");
    }

    public static TrellisException actionTokenInvalid() {
        return new TrellisException(IdentityErrorCode.ACTION_TOKEN_INVALID, "Action token is invalid or expired");
    }
}
