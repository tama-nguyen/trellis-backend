package io.trellis.platform.security.jwt;

import io.trellis.common.context.Actor;
import io.trellis.common.context.IdentityOrigin;
import io.trellis.common.security.Permission;
import io.trellis.common.security.PermissionRegistry;
import io.trellis.platform.security.SecurityProperties;
import io.trellis.platform.security.authz.DeclaredPermissions;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

/**
 * Turns a validated JWT into an {@link Actor} without losing the provenance of its claims.
 *
 * <p>JWTs come from the configured OpenID Connect (OIDC) provider. The bundled local stack uses
 * Keycloak.
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

  public Actor toActor(final Jwt token) {
    final String issuer = token.getClaimAsString(JwtClaimNames.ISS);
    if (issuer == null || issuer.isBlank()) {
      throw new BadCredentialsException("Invalid identity claims");
    }
    final Set<String> roles = stringsAtPath(token.getClaims(), properties.rolesClaim());
    final Set<Permission> permissions =
        DeclaredPermissions.parse(
            stringsAtPath(token.getClaims(), properties.permissionsClaim()),
            permissionRegistry,
            DeclaredPermissions.SOURCE_TOKEN);
    final String clientId = token.getClaimAsString(properties.serviceClaim());
    if (clientId != null && !clientId.isBlank()) {
      return Actor.service(OidcPrincipalIdEncoder.serviceId(issuer, clientId), roles, permissions);
    }
    if (token.getSubject() == null || token.getSubject().isBlank()) {
      throw new BadCredentialsException("Invalid identity claims");
    }
    return Actor.user(
        OidcPrincipalIdEncoder.userId(issuer, token.getSubject()),
        roles,
        permissions,
        IdentityOrigin.OIDC);
  }

  /**
   * Reads the string collection at a dotted claim path; anything else at that path reads as absent.
   */
  private static Set<String> stringsAtPath(
      final Map<String, Object> claims, final String dottedPath) {
    Object node = claims;
    for (final String segment : dottedPath.split(CLAIM_PATH_SEPARATOR)) {
      if (!(node instanceof Map<?, ?> map)) {
        return Set.of();
      }
      node = map.get(segment);
    }
    final Set<String> values = new HashSet<>();
    if (node instanceof Collection<?> items) {
      items.forEach(item -> values.add(String.valueOf(item)));
    }
    return values;
  }
}
