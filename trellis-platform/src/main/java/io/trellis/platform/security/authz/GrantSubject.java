package io.trellis.platform.security.authz;

import io.trellis.common.context.Actor;
import java.time.Instant;
import java.util.Optional;

/**
 * The subject of a stored grant, at the instant a decision is made.
 *
 * <p>Only users and services can hold grants: the system actor bypasses authorization and an anonymous caller
 * holds nothing, so {@link #of} returns empty for both rather than letting an impossible subject reach SQL.
 * Carrying {@code asOf} keeps single-row checks and list queries on the same clock when judging expiry.
 */
public record GrantSubject(GrantSubjectType type, String id, Instant asOf) {

    public static Optional<GrantSubject> of(Actor actor, Instant asOf) {
        return switch (actor) {
            case Actor.UserActor user -> Optional.of(new GrantSubject(GrantSubjectType.USER, user.id(), asOf));
            case Actor.ServiceActor service ->
                Optional.of(new GrantSubject(GrantSubjectType.SERVICE, service.id(), asOf));
            case Actor.SystemActor ignored -> Optional.empty();
            case Actor.AnonymousActor ignored -> Optional.empty();
        };
    }
}
