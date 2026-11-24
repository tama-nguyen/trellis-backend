package io.trellis.identity;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Native user, session and user-action settings. */
@ConfigurationProperties(prefix = "trellis.identity")
public record IdentityProperties(
        @DefaultValue("PT15M") Duration accessTokenTtl,
        @DefaultValue("P30D") Duration refreshTokenTtl,
        @DefaultValue("PT30M") Duration verifyEmailTokenTtl,
        @DefaultValue("PT15M") Duration resetPasswordTokenTtl,
        @DefaultValue("12") int bcryptStrength,
        @DefaultValue("learner") String defaultRole,
        @DefaultValue("http://localhost:13000/verify-email") URI verifyEmailUri,

        @DefaultValue("http://localhost:13000/reset-password")
        URI resetPasswordUri,

        @DefaultValue("no-reply@example.test") String mailFrom) {
    public IdentityProperties {
        requirePositive(accessTokenTtl, "access-token-ttl");
        requirePositive(refreshTokenTtl, "refresh-token-ttl");
        requirePositive(verifyEmailTokenTtl, "verify-email-token-ttl");
        requirePositive(resetPasswordTokenTtl, "reset-password-token-ttl");
        if (bcryptStrength < 10 || bcryptStrength > 16) {
            throw new IllegalArgumentException("trellis.identity.bcrypt-strength must be between 10 and 16");
        }
        if (defaultRole == null || defaultRole.isBlank()) {
            throw new IllegalArgumentException("trellis.identity.default-role must not be blank");
        }
    }

    private static void requirePositive(Duration value, String key) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("trellis.identity." + key + " must be positive");
        }
    }
}
