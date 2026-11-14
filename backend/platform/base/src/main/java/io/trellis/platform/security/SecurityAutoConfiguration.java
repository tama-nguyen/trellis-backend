package io.trellis.platform.security;

import io.micrometer.core.instrument.MeterRegistry;
import io.trellis.common.security.AuthorizationService;
import io.trellis.common.security.KnownPermissions;
import io.trellis.common.security.Permission;
import io.trellis.platform.security.authz.ConfiguredPermissionResolver;
import io.trellis.platform.security.authz.DefaultAuthorizationService;
import io.trellis.platform.security.authz.PermissionCatalogScanner;
import io.trellis.platform.security.authz.PermissionResolver;
import io.trellis.platform.security.jwt.ClaimActorMapper;
import io.trellis.platform.security.jwt.SecurityContextActorResolver;
import java.util.List;
import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Profiles;

/**
 * Security wiring that every deployable needs: actor resolution, the permission catalogue and the deny-by-default
 * {@code AuthorizationService}. It has no servlet dependency on purpose, so a consumer or upgrade job authorises
 * use cases exactly like the API server does; the HTTP edge (filter chain, JWT decoder bean, problem mapping)
 * lives in {@code io.trellis.platform.web.security.WebSecurityAutoConfiguration}, which runs after this one.
 */
@AutoConfiguration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityAutoConfiguration {
    /** Profiles allowed to start without an issuer; every token is then rejected. */
    public static final Profiles LENIENT_PROFILES = Profiles.of("dev | test");
    /** Packages scanned for {@code @PermissionCatalog}; the init script renames it with the project. */
    static final List<String> CATALOG_PACKAGES = List.of("io.trellis");

    @Bean
    ClaimActorMapper claimActorMapper(SecurityProperties properties) {
        return new ClaimActorMapper(properties);
    }

    @Bean
    ActorResolver securityContextActorResolver(ClaimActorMapper mapper) {
        return new SecurityContextActorResolver(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    KnownPermissions knownPermissions() {
        return PermissionCatalogScanner.scan(CATALOG_PACKAGES, SecurityAutoConfiguration.class.getClassLoader());
    }

    @Bean
    @ConditionalOnMissingBean
    PermissionResolver permissionResolver(SecurityProperties properties, KnownPermissions known) {
        ConfiguredPermissionResolver resolver = new ConfiguredPermissionResolver(properties.rolePermissions());
        properties.rolePermissions().values().stream()
                .flatMap(List::stream)
                .map(Permission::of)
                .filter(p -> !known.isDeclared(p))
                .findFirst()
                .ifPresent(p -> {
                    throw new IllegalStateException(
                            "trellis.security.role-permissions grants undeclared permission '" + p + "'");
                });
        return resolver;
    }

    @Bean
    @ConditionalOnMissingBean
    AuthorizationService authorizationService(PermissionResolver resolver, MeterRegistry meters) {
        return new DefaultAuthorizationService(resolver, meters);
    }

    /** Redacts sensitive and unclassified {@code trellis.*} keys in actuator env/configprops output. */
    @Bean
    SanitizingFunction trellisSensitivePropertiesSanitizer() {
        return data ->
                isTrellisKey(data) && SensitiveProperties.isSensitive(data.getKey()) ? data.withSanitizedValue() : data;
    }

    private static boolean isTrellisKey(SanitizableData data) {
        return data.getKey() != null && data.getKey().startsWith("trellis.");
    }
}
