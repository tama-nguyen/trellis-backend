package io.trellis.notice.api.security;

import io.trellis.common.security.PermissionCatalog;

/**
 * Permissions of the Notice module. Granted to roles in {@code trellis.security.role-permissions}. "Staff" is
 * not a role check in code: whoever holds {@link #READ_STAFF} sees STAFF notices.
 */
@PermissionCatalog
public final class NoticePermissions {
    /** Read published notices addressed to everyone. */
    public static final String READ = "notice:read";

    /** Also read published notices addressed to staff. Without it a STAFF notice answers 404, not 403. */
    public static final String READ_STAFF = "notice:read-staff";

    /** Create, edit, publish and archive notices; see drafts and archived notices. */
    public static final String MANAGE = "notice:manage";

    private NoticePermissions() {}
}
