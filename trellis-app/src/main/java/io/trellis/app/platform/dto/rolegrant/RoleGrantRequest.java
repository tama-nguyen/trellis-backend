package io.trellis.app.platform.dto.rolegrant;

import io.trellis.platform.security.authz.GrantSubjectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Request to grant one role at one scope. */
public record RoleGrantRequest(
        @NotNull GrantSubjectType subjectType,
        @NotBlank String subjectId,
        @NotBlank String role,
        @NotBlank String scopeType,
        @NotBlank String scopeId,
        Instant expiresAt) {}
