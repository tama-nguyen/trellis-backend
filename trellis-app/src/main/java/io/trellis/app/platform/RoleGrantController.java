package io.trellis.app.platform;

import io.trellis.app.platform.application.support.PageSize;
import io.trellis.app.platform.application.usecase.rolegrant.GrantRoleUseCase;
import io.trellis.app.platform.application.usecase.rolegrant.ListRoleGrantsUseCase;
import io.trellis.app.platform.application.usecase.rolegrant.RevokeRoleGrantUseCase;
import io.trellis.app.platform.dto.rolegrant.RoleGrantPage;
import io.trellis.app.platform.dto.rolegrant.RoleGrantRequest;
import io.trellis.app.platform.dto.rolegrant.RoleGrantView;
import io.trellis.common.context.RequestContext;
import io.trellis.common.http.HttpHeaderNames;
import io.trellis.common.security.ResourceScope;
import io.trellis.platform.security.authz.GrantSubjectType;
import io.trellis.platform.security.authz.RoleGrantFilter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Operator surface for resource-scoped role grants. */
@RestController
@RequestMapping("/api/platform/role-grants")
class RoleGrantController {
  private final ListRoleGrantsUseCase listRoleGrants;
  private final GrantRoleUseCase grantRole;
  private final RevokeRoleGrantUseCase revokeRoleGrant;

  RoleGrantController(
      ListRoleGrantsUseCase listRoleGrants,
      GrantRoleUseCase grantRole,
      RevokeRoleGrantUseCase revokeRoleGrant) {
    this.listRoleGrants = listRoleGrants;
    this.grantRole = grantRole;
    this.revokeRoleGrant = revokeRoleGrant;
  }

  @GetMapping
  RoleGrantPage list(
      RequestContext ctx,
      @RequestParam(defaultValue = PageSize.DEFAULT_TEXT) int first,
      @RequestParam(required = false) String after,
      @RequestParam(required = false) GrantSubjectType subjectType,
      @RequestParam(required = false) String subjectId,
      @RequestParam(required = false) String role,
      @RequestParam(required = false) String scopeType,
      @RequestParam(required = false) String scopeId) {
    return RoleGrantPage.of(
        listRoleGrants.execute(
            ctx,
            new RoleGrantFilter(subjectType, subjectId, role, scopeType, scopeId),
            after,
            first));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  RoleGrantView grant(
      RequestContext ctx,
      @RequestHeader(HttpHeaderNames.IDEMPOTENCY_KEY) String idempotencyKey,
      @Valid @RequestBody RoleGrantRequest request) {
    return RoleGrantView.of(
        grantRole.execute(
            ctx,
            request.subjectType(),
            request.subjectId(),
            request.role(),
            ResourceScope.of(request.scopeType(), request.scopeId()),
            request.expiresAt(),
            idempotencyKey));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void revoke(RequestContext ctx, @PathVariable long id) {
    revokeRoleGrant.execute(ctx, id);
  }
}
