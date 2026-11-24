package io.trellis.common.security;

/** Permissions of platform operations. */
@PermissionCatalog
public final class PlatformPermissions {
    /** Re-publish dead-lettered events. */
    public static final String EVENTS_REPLAY = "platform.events:replay";
    /** Rebuild a read model from the source of truth. */
    public static final String READ_MODEL_REBUILD = "platform.read-models:rebuild";
    /** Search the append-only audit trail. */
    public static final String AUDIT_READ = "platform.audit:read";
    /** Create, list and revoke stored role grants. */
    public static final String ROLE_GRANTS_MANAGE = "platform.role-grants:manage";

    private PlatformPermissions() {}
}
