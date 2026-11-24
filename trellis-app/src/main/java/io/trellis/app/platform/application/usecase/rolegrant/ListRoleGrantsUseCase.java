package io.trellis.app.platform.application.usecase.rolegrant;

import io.trellis.app.platform.application.support.PageSize;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.common.page.CursorPage;
import io.trellis.common.security.PlatformPermissions;
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

  private final RoleGrantStore grants;

  @RequiresPermission(anyOf = PlatformPermissions.ROLE_GRANTS_MANAGE)
  @Transactional(readOnly = true)
  public CursorPage<RoleGrant> execute(
      RequestContext ctx, RoleGrantFilter filter, String after, int first) {
    PageSize.require(first);
    return grants.page(filter, after, first);
  }
}
