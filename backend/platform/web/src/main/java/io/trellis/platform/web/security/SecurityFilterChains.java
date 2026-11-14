package io.trellis.platform.web.security;

import io.trellis.common.constants.TrellisHeaders;
import io.trellis.platform.security.SecurityAutoConfiguration;
import io.trellis.platform.security.SecurityProperties;
import io.trellis.platform.security.jwt.ClaimActorMapper;
import io.trellis.platform.security.jwt.JwtDecoders;
import io.trellis.platform.web.error.ProblemResponseWriter;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Two chains. The management chain (actuator, on its own port) exposes only health, info and the Prometheus
 * scrape anonymously: they carry no secrets and the port is not routed publicly. The API chain is a stateless
 * resource server where everything requires a token unless marked {@code @PublicEndpoint}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
public class SecurityFilterChains {
    private static final String[] ANONYMOUS_ACTUATOR_ENDPOINTS = {"health", "info", "prometheus"};
    private static final String ALL_PATHS = "/**";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(auth -> auth.requestMatchers(EndpointRequest.to(ANONYMOUS_ACTUATOR_ENDPOINTS))
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 20)
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            SecurityProperties properties,
            JwtDecoder jwtDecoder,
            ClaimActorMapper actorMapper,
            ProblemResponseWriter problems,
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping)
            throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, error) -> problems.write(request, response, error);
        AccessDeniedHandler deniedHandler = (request, response, error) -> problems.write(request, response, error);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> actorMapper.authorities(jwt).stream()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList());

        return http.securityMatcher(ALL_PATHS)
                // Stateless bearer-token API: no cookies, so no CSRF surface. The web BFF owns CSRF for browsers.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                new PublicEndpointRequestMatcher(handlerMapping, properties.publicPaths()))
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(
                        rs -> rs.jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(converter))
                                .authenticationEntryPoint(entryPoint)
                                .accessDeniedHandler(deniedHandler))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .headers(h -> h.contentSecurityPolicy(
                                csp -> csp.policyDirectives(properties.headers().contentSecurityPolicy()))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true)
                                .maxAgeInSeconds(
                                        properties.headers().hstsMaxAge().toSeconds()))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecurityProperties properties, Environment environment) {
        return JwtDecoders.create(properties, environment.acceptsProfiles(SecurityAutoConfiguration.LENIENT_PROFILES));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        SecurityProperties.Cors cors = properties.cors();
        if (!cors.allowedOrigins().isEmpty()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(cors.allowedOrigins());
            config.setAllowedMethods(cors.allowedMethods());
            config.setAllowedHeaders(cors.allowedHeaders());
            config.setExposedHeaders(List.of(TrellisHeaders.CORRELATION_ID, TrellisHeaders.RETRY_AFTER));
            config.setMaxAge(cors.maxAge());
            source.registerCorsConfiguration(ALL_PATHS, config);
        }
        return source;
    }
}
