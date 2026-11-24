package io.trellis.platform.security.authz;

import io.micrometer.core.instrument.MeterRegistry;
import io.trellis.common.context.Actor;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.AuthorizationDecision;
import io.trellis.common.security.AuthorizationService;
import io.trellis.common.security.PermissionRequirement;
import io.trellis.common.security.ResourceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deny by default. The system actor is allowed in a branch subclasses cannot change (the class is
 * final), so no customisation can forget the bypass or open it by accident. Emits {@code
 * trellis.authz.decisions{outcome, actor_type}}.
 */
public final class DefaultAuthorizationService implements AuthorizationService {
  private static final Logger log = LoggerFactory.getLogger(DefaultAuthorizationService.class);
  private static final String METRIC = "trellis.authz.decisions";

  private final PermissionResolver permissions;
  private final MeterRegistry meters;

  public DefaultAuthorizationService(PermissionResolver permissions, MeterRegistry meters) {
    this.permissions = permissions;
    this.meters = meters;
  }

  @Override
  public AuthorizationDecision decide(
      RequestContext ctx, PermissionRequirement requirement, ResourceScope scope) {
    AuthorizationDecision decision =
        switch (ctx.actor()) {
          case Actor.SystemActor system -> AuthorizationDecision.allow("system actor");
          case Actor.AnonymousActor anonymous -> AuthorizationDecision.deny("anonymous actor");
          case Actor.UserActor user -> byPermissions(user, requirement, scope);
          case Actor.ServiceActor service -> byPermissions(service, requirement, scope);
        };
    meters
        .counter(
            METRIC,
            "outcome",
            decision.allowed() ? "allow" : "deny",
            "actor_type",
            ctx.actor().type().name(),
            "scope_type",
            scope.type())
        .increment();
    if (!decision.allowed()) {
      log.atInfo()
          .addKeyValue("requirement", requirement)
          .addKeyValue("scopeType", scope.type())
          .addKeyValue("reason", decision.reason())
          .log("Authorization denied");
    }
    return decision;
  }

  private AuthorizationDecision byPermissions(
      Actor actor, PermissionRequirement requirement, ResourceScope scope) {
    return requirement.isSatisfiedBy(permissions.permissionsOf(actor, scope))
        ? AuthorizationDecision.allow("permission granted")
        : AuthorizationDecision.deny("missing permission");
  }
}
