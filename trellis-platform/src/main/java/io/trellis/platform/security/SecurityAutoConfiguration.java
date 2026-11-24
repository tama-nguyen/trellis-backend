package io.trellis.platform.security;

import io.micrometer.core.instrument.MeterRegistry;
import io.trellis.common.config.ConfigKeys;
import io.trellis.common.page.CursorCodec;
import io.trellis.common.security.AuthorizationService;
import io.trellis.common.security.PermissionRegistry;
import io.trellis.platform.security.authz.DefaultAuthorizationService;
import io.trellis.platform.security.authz.GrantCache;
import io.trellis.platform.security.authz.JdbcRoleGrantStore;
import io.trellis.platform.security.authz.PermissionCatalogScanner;
import io.trellis.platform.security.authz.PermissionResolver;
import io.trellis.platform.security.authz.RoleGrantStore;
import io.trellis.platform.security.authz.RolePermissionVerifier;
import io.trellis.platform.security.authz.StoredPermissionResolver;
import io.trellis.platform.security.jwt.JwtActorMapper;
import io.trellis.platform.security.jwt.SecurityContextActorResolver;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.simple.JdbcClient;

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
    @ConditionalOnMissingBean
    PermissionRegistry permissionRegistry() {
        return PermissionCatalogScanner.scan(CATALOG_PACKAGES, SecurityAutoConfiguration.class.getClassLoader());
    }

    @Bean
    JwtActorMapper jwtActorMapper(SecurityProperties properties, PermissionRegistry permissionRegistry) {
        return new JwtActorMapper(properties, permissionRegistry);
    }

    @Bean
    ActorResolver securityContextActorResolver() {
        return new SecurityContextActorResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    GrantCache grantCache(SecurityProperties properties) {
        return new GrantCache(
                properties.grants().cacheTtl(), properties.grants().cacheMaxSize());
    }

    @Bean
    @ConditionalOnMissingBean
    RoleGrantStore roleGrantStore(
            JdbcClient jdbc, CursorCodec cursors, GrantCache cache, PermissionRegistry permissionRegistry) {
        return new JdbcRoleGrantStore(jdbc, cursors, cache, permissionRegistry);
    }

    @Bean
    RolePermissionVerifier rolePermissionVerifier(JdbcClient jdbc, PermissionRegistry permissionRegistry) {
        return new RolePermissionVerifier(jdbc, permissionRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    PermissionResolver permissionResolver(RoleGrantStore grants, GrantCache cache, Clock clock) {
        return new StoredPermissionResolver(grants, cache, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    AuthorizationService authorizationService(PermissionResolver resolver, MeterRegistry meters) {
        return new DefaultAuthorizationService(resolver, meters);
    }

    /** Redacts sensitive and unclassified product keys in actuator env/configprops output. */
    @Bean
    SanitizingFunction productPropertiesSanitizer() {
        return data -> ConfigKeys.isProductKey(data.getKey()) && SensitiveProperties.isSensitive(data.getKey())
                ? data.withSanitizedValue()
                : data;
    }
}
