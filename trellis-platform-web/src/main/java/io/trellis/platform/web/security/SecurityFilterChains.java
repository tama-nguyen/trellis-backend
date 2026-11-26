
package io.trellis.platform.web.security;

import io.trellis.common.http.HttpHeaderNames;
import io.trellis.platform.aspect.ratelimit.RateLimitEngine;
import io.trellis.platform.security.SecurityAutoConfiguration;
import io.trellis.platform.security.SecurityProperties;
import io.trellis.platform.security.jwt.ActorJwtAuthenticationToken;
import io.trellis.platform.security.jwt.JwtActorMapper;
import io.trellis.platform.security.jwt.JwtDecoders;
import io.trellis.platform.web.error.ProblemResponseWriter;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Configures two independent security filter chains.
 *
 * <p>The management chain permits anonymous access to health, info and Prometheus endpoints.
 * The management port is intended for private infrastructure access only.
 *
 * <p>The API chain is a stateless resource server. Requests require authentication unless
 * explicitly declared public through {@code @PublicEndpoint} or security configuration.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
@Import({
  HttpRateLimitConfiguration.class,
  InternalKeyFilter.class
})
public class SecurityFilterChains {

  private static final String[] ANONYMOUS_ACTUATOR_ENDPOINTS = {
    "health", "info", "prometheus"
  };

  private static final String ALL_PATHS = "/**";

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE + 10)
  SecurityFilterChain managementSecurityFilterChain(final HttpSecurity http)
    throws Exception {
    return http.securityMatcher(EndpointRequest.toAnyEndpoint())
      .authorizeHttpRequests(
        authorization ->
          authorization
            .requestMatchers(EndpointRequest.to(ANONYMOUS_ACTUATOR_ENDPOINTS))
            .permitAll()
            .anyRequest()
            .denyAll())
      .csrf(csrf -> csrf.disable())
      .sessionManagement(
        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .build();
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE + 20)
  SecurityFilterChain apiSecurityFilterChain(
    final HttpSecurity http,
    final SecurityProperties securityProperties,
    final RateLimitEngine rateLimitEngine,
    final HttpRateLimitConfiguration rateLimitConfiguration,
    final JwtDecoder jwtDecoder,
    final JwtActorMapper jwtActorMapper,
    final ObjectProvider<AuthenticationProvider> authenticationProviders,
    final InternalKeyFilter internalKeyFilter,
    final ProblemResponseWriter problemResponseWriter,
    @Qualifier("requestMappingHandlerMapping")
    final RequestMappingHandlerMapping requestMappingHandlerMapping)
    throws Exception {

    final AuthenticationEntryPoint authenticationEntryPoint =
      (request, response, exception) ->
        problemResponseWriter.write(request, response, exception);

    final AccessDeniedHandler accessDeniedHandler =
      (request, response, exception) ->
        problemResponseWriter.write(request, response, exception);

    final Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter =
      jwt -> new ActorJwtAuthenticationToken(jwt, jwtActorMapper.toActor(jwt));

    final JwtAuthenticationProvider jwtAuthenticationProvider =
      new JwtAuthenticationProvider(jwtDecoder);

    jwtAuthenticationProvider.setJwtAuthenticationConverter(jwtAuthenticationConverter);

    final List<AuthenticationProvider> bearerAuthenticationProviders =
      Stream.concat(
          authenticationProviders
            .orderedStream()
            .filter(
              provider ->
                provider.supports(BearerTokenAuthenticationToken.class)),
          Stream.of(jwtAuthenticationProvider))
        .toList();

    final AuthenticationManager bearerAuthenticationManager =
      new ProviderManager(bearerAuthenticationProviders);

    return http.securityMatcher(ALL_PATHS)

      // Stateless bearer-token API. The web BFF owns browser CSRF protection.
      .csrf(csrf -> csrf.disable())
      .sessionManagement(
        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .cors(Customizer.withDefaults())

      .addFilterAfter(internalKeyFilter, CorsFilter.class)
      .addFilterAfter(
        new HttpRateLimitFilter(
          rateLimitEngine,
          new ClientAddressResolver(rateLimitConfiguration.trustedProxies()),
          problemResponseWriter),
        InternalKeyFilter.class)

      .authorizeHttpRequests(
        authorization ->
          authorization
            .requestMatchers(
              new PublicEndpointRequestMatcher(
                requestMappingHandlerMapping,
                securityProperties.publicPaths()))
            .permitAll()
            .anyRequest()
            .authenticated())

      .oauth2ResourceServer(
        resourceServer ->
          resourceServer
            .authenticationManagerResolver(
              request -> bearerAuthenticationManager)
            .authenticationEntryPoint(authenticationEntryPoint)
            .accessDeniedHandler(accessDeniedHandler))

      .exceptionHandling(
        exceptions ->
          exceptions
            .authenticationEntryPoint(authenticationEntryPoint)
            .accessDeniedHandler(accessDeniedHandler))

      .headers(
        headers ->
          headers
            .addHeaderWriter(
              new StaticHeadersWriter(
                HttpHeaderNames.PERMISSIONS_POLICY,
                "camera=(), microphone=(), geolocation=()"))
            .contentSecurityPolicy(
              contentSecurityPolicy ->
                contentSecurityPolicy.policyDirectives(
                  securityProperties.headers().contentSecurityPolicy()))
            .httpStrictTransportSecurity(
              hsts ->
                hsts.includeSubDomains(true)
                  .maxAgeInSeconds(
                    securityProperties.headers().hstsMaxAge().toSeconds()))
            .frameOptions(frame -> frame.deny())
            .referrerPolicy(
              referrer ->
                referrer.policy(
                  ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))

      .build();
  }

  /**
   * Prevents servlet-container registration because this filter is registered explicitly
   * in the Spring Security filter chain.
   */
  @Bean
  FilterRegistrationBean<InternalKeyFilter> internalKeyFilterRegistration(
    final InternalKeyFilter internalKeyFilter) {

    final FilterRegistrationBean<InternalKeyFilter> filterRegistration =
      new FilterRegistrationBean<>(internalKeyFilter);

    filterRegistration.setEnabled(false);

    return filterRegistration;
  }

  @Bean
  JwtDecoder jwtDecoder(
    final SecurityProperties securityProperties, final Environment environment) {

    return JwtDecoders.create(
      securityProperties,
      environment.acceptsProfiles(SecurityAutoConfiguration.LENIENT_PROFILES));
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
    final SecurityProperties securityProperties) {

    final UrlBasedCorsConfigurationSource corsConfigurationSource =
      new UrlBasedCorsConfigurationSource();

    final SecurityProperties.Cors corsProperties = securityProperties.cors();

    if (!corsProperties.allowedOrigins().isEmpty()) {
      final CorsConfiguration corsConfiguration = new CorsConfiguration();

      corsConfiguration.setAllowedOrigins(corsProperties.allowedOrigins());
      corsConfiguration.setAllowedMethods(corsProperties.allowedMethods());
      corsConfiguration.setAllowedHeaders(corsProperties.allowedHeaders());
      corsConfiguration.setExposedHeaders(
        List.of(HttpHeaderNames.CORRELATION_ID, HttpHeaderNames.RETRY_AFTER));
      corsConfiguration.setMaxAge(corsProperties.maxAge());

      corsConfigurationSource.registerCorsConfiguration(ALL_PATHS, corsConfiguration);
    }

    return corsConfigurationSource;
  }
}
