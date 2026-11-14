package io.trellis.platform.security;

import io.micrometer.core.instrument.MeterRegistry;
import io.trellis.common.security.AuthorizationService;
import io.trellis.common.security.KnownPermissions;
import io.trellis.common.security.Permission;
import io.trellis.platform.security.authz.ConfiguredPermissionResolver;
import io.trellis.platform.security.authz.DefaultAuthorizationService;
import io.trellis.platform.security.authz.PermissionCatalogScanner;
import io.trellis.platform.security.authz.PermissionResolver;
import io.trellis.platform.web.security.SecurityFilterChains;
import io.trellis.platform.web.security.SecurityProblemMapper;
import io.trellis.platform.security.SensitiveProperties;
import io.trellis.platform.security.jwt.ClaimActorMapper;
import io.trellis.platform.security.jwt.SecurityContextActorResolver;
import io.trellis.platform.web.WebAutoConfiguration;
import io.trellis.platform.security.ActorResolver;
import java.util.List;
import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Profiles;

/**
 * Security wiring. Runs before the web module so its {@link ActorResolver} replaces the anonymous default, and
 * before Spring Boot's security auto-configuration so ours is the only filter chain and JWT decoder.
 */
@AutoConfiguration(
        before = {
            WebAutoConfiguration.class,
            org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class,
            ManagementWebSecurityAutoConfiguration.class
        })
@EnableConfigurationProperties(SecurityProperties.class)
@Import(SecurityFilterChains.class)
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

    @Bean
    @Order(WebAutoConfiguration.TRELLIS_MAPPER_ORDER + 10)
    SecurityProblemMapper securityProblemMapper() {
        return new SecurityProblemMapper();
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
