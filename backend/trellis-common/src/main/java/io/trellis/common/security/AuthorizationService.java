package io.trellis.common.security;

import io.trellis.common.context.RequestContext;
import io.trellis.common.error.ForbiddenException;

/**
 * The only place that decides whether an actor may do something. Deny by default: anything not explicitly
 * granted is refused. Called by the {@code @RequiresPermission} aspect, or directly when the decision depends
 * on loaded data (e.g. "owner of this notice").
 */
public interface AuthorizationService {

    AuthorizationDecision decide(RequestContext ctx, PermissionRequirement requirement);

    /** @throws ForbiddenException when denied */
    default void require(RequestContext ctx, PermissionRequirement requirement) {
        if (!decide(ctx, requirement).allowed()) {
            throw new ForbiddenException();
        }
    }
}
