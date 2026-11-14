package io.trellis.platform.security.jwt;

import io.trellis.common.context.Actor;
import io.trellis.platform.security.SecurityProperties;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;

/** Turns a validated JWT into an {@link Actor}: roles and direct permissions become authorities. */
public final class ClaimActorMapper {
    private static final String PATH_SEPARATOR = "\\.";

    private final SecurityProperties properties;

    public ClaimActorMapper(SecurityProperties properties) {
        this.properties = properties;
    }

    public Actor toActor(Jwt jwt) {
        Set<String> authorities = new HashSet<>();
        authorities.addAll(stringsAt(jwt.getClaims(), properties.rolesClaim()));
        authorities.addAll(stringsAt(jwt.getClaims(), properties.permissionsClaim()));
        String serviceId = jwt.getClaimAsString(properties.serviceClaim());
        if (serviceId != null && !serviceId.isBlank()) {
            return Actor.service(serviceId, authorities);
        }
        return Actor.user(jwt.getSubject(), authorities);
    }

    public Set<String> authorities(Jwt jwt) {
        return toActor(jwt).authorities();
    }

    private static Set<String> stringsAt(Map<String, Object> claims, String dottedPath) {
        Object node = claims;
        for (String segment : dottedPath.split(PATH_SEPARATOR)) {
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
