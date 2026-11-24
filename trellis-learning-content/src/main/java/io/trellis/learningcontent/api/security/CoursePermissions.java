package io.trellis.learningcontent.api.security;

import io.trellis.common.security.PermissionCatalog;

/**
 * Permissions of the Course module. Roles are seeded through {@code role_permission}, seeded by
 * {@code db/changelog/learning-content/002-course-roles.sql}; a permission no catalog declares
 * fails setup, and a {@code @RequiresPermission} typo fails startup.
 */
@PermissionCatalog
public final class CoursePermissions {
  /** Read the courses a caller is allowed to see. */
  public static final String READ = "course:read";

  /** Create, edit and archive courses; see drafts and archived courses. */
  public static final String MANAGE = "course:manage";

  private CoursePermissions() {}
}
