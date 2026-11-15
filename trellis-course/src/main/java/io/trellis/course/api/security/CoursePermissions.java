package io.trellis.course.api.security;

import io.trellis.common.security.PermissionCatalog;

/**
 * Permissions of the Course module. Granted to roles in {@code trellis.security.role-permissions}; a role that
 * names a permission no catalog declares fails startup, and so does a {@code @RequiresPermission} typo.
 */
@PermissionCatalog
public final class CoursePermissions {
    /** Read the courses a caller is allowed to see. */
    public static final String READ = "course:read";

    /** Create, edit and archive courses; see drafts and archived courses. */
    public static final String MANAGE = "course:manage";

    private CoursePermissions() {}
}
