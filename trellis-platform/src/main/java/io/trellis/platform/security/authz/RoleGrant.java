package io.trellis.platform.security.authz;

import io.trellis.common.security.ResourceScope;
import java.time.Instant;

/** A role held by one subject at one resource scope. */
public record RoleGrant(
        long id,
        GrantSubjectType subjectType,
        String subjectId,
        String role,
        ResourceScope scope,
        Instant grantedAt,
        String grantedBy,
        Instant expiresAt) {}
