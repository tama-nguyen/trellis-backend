package io.trellis.platform.security.authz;

/** Optional filters for the operator grant list. */
public record RoleGrantFilter(
        GrantSubjectType subjectType, String subjectId, String role, String scopeType, String scopeId) {}
