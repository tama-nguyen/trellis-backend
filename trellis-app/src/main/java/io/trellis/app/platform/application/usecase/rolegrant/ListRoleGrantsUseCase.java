package io.trellis.app.platform.application.usecase.rolegrant;

import io.trellis.app.platform.application.support.PageSize;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.page.CursorPage;
import io.trellis.common.security.PlatformPermissions;
import io.trellis.common.security.SecurityAction;
import io.trellis.platform.security.authz.RoleGrant;
import io.trellis.platform.security.authz.RoleGrantFilter;
import io.trellis.platform.security.authz.RoleGrantStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One keyset page of grants, for an operator answering "who can do what here". */
@Service
@RequiredArgsConstructor
public class ListRoleGrantsUseCase {

  private final RoleGrantStore roleGrantStore;

  @SecurityAudited(value = SecurityAction.ROLE_GRANT_LIST, recordSuccess = true)
  @RequiresPermission(anyOf = PlatformPermissions.ROLE_GRANTS_MANAGE)
  @Transactional(readOnly = true)
  public CursorPage<RoleGrant> execute(
      final RequestContext ctx, final RoleGrantFilter filter, final String after, final int first) {
    PageSize.require(first);

    return roleGrantStore.page(filter, after, first);
  }
}
