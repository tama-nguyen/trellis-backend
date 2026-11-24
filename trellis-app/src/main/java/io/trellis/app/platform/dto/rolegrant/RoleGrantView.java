package io.trellis.app.platform.dto.rolegrant;

import io.trellis.platform.security.authz.GrantSubjectType;
import io.trellis.platform.security.authz.RoleGrant;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Operator view of one stored role grant. */
public record RoleGrantView(
        @NotNull long id,
        @NotNull GrantSubjectType subjectType,
        @NotNull String subjectId,
        @NotNull String role,
        @NotNull String scopeType,
        @NotNull String scopeId,
        @NotNull Instant grantedAt,
        @NotNull String grantedBy,
        Instant expiresAt) {
    public static RoleGrantView of(RoleGrant grant) {
        return new RoleGrantView(
                grant.id(),
                grant.subjectType(),
                grant.subjectId(),
                grant.role(),
                grant.scope().type(),
                grant.scope().id(),
                grant.grantedAt(),
                grant.grantedBy(),
                grant.expiresAt());
    }
}
