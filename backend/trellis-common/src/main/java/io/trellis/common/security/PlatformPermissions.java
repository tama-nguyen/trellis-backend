package io.trellis.common.security;

/** Permissions of platform operations (event replay, rebuilds). */
@PermissionCatalog
public final class PlatformPermissions {
    /** Re-publish dead-lettered events. */
    public static final String EVENTS_REPLAY = "platform.events:replay";
    /** Rebuild a read model from the source of truth. */
    public static final String READ_MODEL_REBUILD = "platform.read-models:rebuild";

    private PlatformPermissions() {}
}
