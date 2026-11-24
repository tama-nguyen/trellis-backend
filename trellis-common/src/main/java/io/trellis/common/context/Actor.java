package io.trellis.common.context;

import io.trellis.common.security.Permission;
import java.util.Objects;
import java.util.Set;

/**
 * Who performs an operation. Sealed so every switch over actors is exhaustive.
 *
 * <p>Roles and direct permissions retain their provenance so an identity-provider role can never be
 * mistaken for a Trellis permission.
 */
public sealed interface Actor
    permits Actor.UserActor, Actor.ServiceActor, Actor.SystemActor, Actor.AnonymousActor {

  ActorType type();

  /** Stable id: user subject, client id, or component name. Empty for anonymous. */
  String id();

  Set<String> roles();

  Set<Permission> permissions();

  static Actor user(final String id, final Set<String> roles, final Set<Permission> permissions) {
    return new UserActor(id, roles, permissions);
  }

  static Actor user(
      final String id,
      final Set<String> roles,
      final Set<Permission> permissions,
      final IdentityOrigin origin) {
    return new UserActor(id, roles, permissions, origin);
  }

  static Actor service(
      final String clientId, final Set<String> roles, final Set<Permission> permissions) {
    return new ServiceActor(clientId, roles, permissions);
  }

  /**
   * The application itself (scheduled jobs, relays, migrations). Bypasses permission checks by
   * design.
   */
  static Actor system(final String component) {
    return new SystemActor(component);
  }

  static Actor anonymous() {
    return AnonymousActor.INSTANCE;
  }

  record UserActor(String id, Set<String> roles, Set<Permission> permissions, IdentityOrigin origin)
      implements Actor {
    public UserActor(final String id, final Set<String> roles, final Set<Permission> permissions) {
      this(id, roles, permissions, IdentityOrigin.NATIVE);
    }

    public UserActor {
      Objects.requireNonNull(origin, "origin");
      requireId(id);
      roles = Set.copyOf(roles);
      permissions = Set.copyOf(permissions);
    }

    @Override
    public ActorType type() {
      return ActorType.USER;
    }
  }

  record ServiceActor(String id, Set<String> roles, Set<Permission> permissions) implements Actor {
    public ServiceActor {
      requireId(id);
      roles = Set.copyOf(roles);
      permissions = Set.copyOf(permissions);
    }

    @Override
    public ActorType type() {
      return ActorType.SERVICE;
    }
  }

  record SystemActor(String id) implements Actor {
    public SystemActor {
      requireId(id);
    }

    @Override
    public ActorType type() {
      return ActorType.SYSTEM;
    }

    @Override
    public Set<String> roles() {
      return Set.of();
    }

    @Override
    public Set<Permission> permissions() {
      return Set.of();
    }
  }

  record AnonymousActor() implements Actor {
    static final AnonymousActor INSTANCE = new AnonymousActor();

    @Override
    public ActorType type() {
      return ActorType.ANONYMOUS;
    }

    @Override
    public String id() {
      return "";
    }

    @Override
    public Set<String> roles() {
      return Set.of();
    }

    @Override
    public Set<Permission> permissions() {
      return Set.of();
    }
  }

  private static void requireId(final String id) {
    if (Objects.requireNonNull(id, "id").isBlank()) {
      throw new IllegalArgumentException("actor id must not be blank");
    }
  }
}
