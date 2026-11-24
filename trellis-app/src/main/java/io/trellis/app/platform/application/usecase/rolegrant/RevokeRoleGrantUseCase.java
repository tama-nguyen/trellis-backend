package io.trellis.app.platform.application.usecase.rolegrant;

import io.trellis.app.platform.PlatformErrorCode;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.TrellisException;
import io.trellis.common.security.PlatformPermissions;
import io.trellis.platform.security.authz.RoleGrantStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Takes a grant away. Revoking one that is already gone is a 404, not a silent success. */
@Service
@RequiredArgsConstructor
public class RevokeRoleGrantUseCase {
    static final String AUDIT_ACTION = "platform.role-grant.revoked";
    static final String AGGREGATE_TYPE = "RoleGrant";

    private final RoleGrantStore grants;

    @RequiresPermission(anyOf = PlatformPermissions.ROLE_GRANTS_MANAGE)
    @Transactional
    @Audited(action = AUDIT_ACTION, aggregateType = AGGREGATE_TYPE, aggregateId = "#id")
    public void execute(RequestContext ctx, long id) {
        if (!grants.revoke(id)) {
            throw new TrellisException(PlatformErrorCode.ROLE_GRANT_NOT_FOUND, "Role grant " + id + " was not found");
        }
    }
}
