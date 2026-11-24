package io.trellis.app.platform.application.usecase.rolegrant;

import io.trellis.app.platform.PlatformErrorCode;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.Idempotent;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.TrellisException;
import io.trellis.common.security.PlatformPermissions;
import io.trellis.common.security.ResourceScope;
import io.trellis.common.security.SecurityAction;
import io.trellis.platform.security.authz.GrantSubjectType;
import io.trellis.platform.security.authz.RoleGrant;
import io.trellis.platform.security.authz.RoleGrantStore;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives a subject a role over one scope. Repeating it with the same key returns the first result.
 */
@Service
@RequiredArgsConstructor
public class GrantRoleUseCase {
  static final String AUDIT_ACTION = "platform.role-grant.granted";
  static final String AGGREGATE_TYPE = "RoleGrant";

  private final RoleGrantStore roleGrantStore;

  @SecurityAudited(value = SecurityAction.ROLE_GRANT, recordSuccess = true)
  @RequiresPermission(anyOf = PlatformPermissions.ROLE_GRANTS_MANAGE)
  @Idempotent(key = "#idempotencyKey")
  @Transactional
  @Audited(action = AUDIT_ACTION, aggregateType = AGGREGATE_TYPE, aggregateId = "#result.id()")
  public RoleGrant execute(
      final RequestContext ctx,
      final GrantSubjectType subjectType,
      final String subjectId,
      final String role,
      final ResourceScope scope,
      final Instant expiresAt,
      final String idempotencyKey) {
    if (expiresAt != null && !expiresAt.isAfter(ctx.requestTime())) {
      throw new TrellisException(
          PlatformErrorCode.INVALID_EXPIRY, "expiresAt must be in the future");
    }
    return roleGrantStore.grant(
        subjectType, subjectId, role, scope, ctx.requestTime(), ctx.actor().id(), expiresAt);
  }
}
