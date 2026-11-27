package io.trellis.notification.api.security;

import io.trellis.common.security.PermissionCatalog;

/**
 * Permissions of the Notification module. Roles are seeded through {@code role_permission}, seeded
 * by {@code db/changelog/notification/002-notification-roles.sql}. "Staff" is not a role check in
 * code: whoever holds {@link #READ_STAFF} sees STAFF notifications.
 */
@PermissionCatalog
public final class NotificationPermissions {
  /** Read published notifications addressed to everyone. */
  public static final String READ = "notification:read";

  /**
   * Also read published notifications addressed to staff. Without it a STAFF notification answers
   * 404, not 403.
   */
  public static final String READ_STAFF = "notification:read-staff";

  /** Create, edit, publish and archive notifications; see drafts and archived notifications. */
  public static final String MANAGE = "notification:manage";

  private NotificationPermissions() {}
}
