package io.trellis.common.context;

import java.util.Objects;
import java.util.Set;

/**
 * Who performs an operation. Sealed so every switch over actors is exhaustive.
 *
 * <p>{@link #authorities()} are the raw roles/scopes from the credential; translating them into permissions
 * is the job of the security module's {@code PermissionResolver}, not of the actor.
 */
public sealed interface Actor permits Actor.UserActor, Actor.ServiceActor, Actor.SystemActor, Actor.AnonymousActor {

    ActorType type();

    /** Stable id: user subject, client id, or component name. Empty for anonymous. */
    String id();

    Set<String> authorities();

    static Actor user(String id, Set<String> authorities) {
        return new UserActor(id, authorities);
    }

    static Actor service(String clientId, Set<String> authorities) {
        return new ServiceActor(clientId, authorities);
    }

    /** The application itself (scheduled jobs, relays, migrations). Bypasses permission checks by design. */
    static Actor system(String component) {
        return new SystemActor(component);
    }

    static Actor anonymous() {
        return AnonymousActor.INSTANCE;
    }

    record UserActor(String id, Set<String> authorities) implements Actor {
        public UserActor {
            requireId(id);
            authorities = Set.copyOf(authorities);
        }

        @Override
        public ActorType type() {
            return ActorType.USER;
        }
    }

    record ServiceActor(String id, Set<String> authorities) implements Actor {
        public ServiceActor {
            requireId(id);
            authorities = Set.copyOf(authorities);
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
        public Set<String> authorities() {
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
        public Set<String> authorities() {
            return Set.of();
        }
    }

    private static void requireId(String id) {
        if (Objects.requireNonNull(id, "id").isBlank()) {
            throw new IllegalArgumentException("actor id must not be blank");
        }
    }
}
