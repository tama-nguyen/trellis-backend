package io.trellis.platform.security.jwt;

import io.trellis.common.context.Actor;
import io.trellis.common.security.Permission;
import io.trellis.common.security.PermissionRegistry;
import io.trellis.platform.security.SecurityProperties;
import io.trellis.platform.security.authz.DeclaredPermissions;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Turns a validated JWT into an {@link Actor} without losing the provenance of its claims.
 *
 * <p>Roles and permissions stay apart on purpose. A role is only a name: it grants nothing until
 * {@code role_permission} maps it. A permission arriving in the token is honoured, but only when a
 * {@code @PermissionCatalog} declares it - otherwise an identity provider could invent authority
 * simply by naming a claim value well.
 */
@RequiredArgsConstructor
public final class JwtActorMapper {
  /** The roles and permissions claims are addressed by a dotted path, split with this regex. */
  private static final String CLAIM_PATH_SEPARATOR = "\\.";

  private final SecurityProperties properties;
  private final PermissionRegistry permissionRegistry;

  public Actor toActor(Jwt token) {
    Set<String> roles = stringsAtPath(token.getClaims(), properties.rolesClaim());
    Set<Permission> permissions =
        DeclaredPermissions.parse(
            stringsAtPath(token.getClaims(), properties.permissionsClaim()),
            permissionRegistry,
            DeclaredPermissions.SOURCE_TOKEN);
    String clientId = token.getClaimAsString(properties.serviceClaim());
    if (clientId != null && !clientId.isBlank()) {
      return Actor.service(clientId, roles, permissions);
    }
    return Actor.user(token.getSubject(), roles, permissions);
  }

  /**
   * Reads the string collection at a dotted claim path; anything else at that path reads as absent.
   */
  private static Set<String> stringsAtPath(Map<String, Object> claims, String dottedPath) {
    Object node = claims;
    for (String segment : dottedPath.split(CLAIM_PATH_SEPARATOR)) {
      if (!(node instanceof Map<?, ?> map)) {
        return Set.of();
      }
      node = map.get(segment);
    }
    Set<String> values = new HashSet<>();
    if (node instanceof Collection<?> items) {
      items.forEach(item -> values.add(String.valueOf(item)));
    }
    return values;
  }
}
