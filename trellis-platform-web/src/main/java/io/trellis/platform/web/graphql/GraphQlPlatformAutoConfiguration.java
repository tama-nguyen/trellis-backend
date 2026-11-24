package io.trellis.platform.web.graphql;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import io.trellis.platform.web.error.ProblemFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * GraphQL wiring. Spring Boot's GraphQL auto-configuration picks up these beans: the interceptor,
 * the exception resolver, the instrumentations and the runtime-wiring configurer. The schema is
 * built in every application mode (also the non-web {@code setup}/{@code migrate} runs), so the
 * scalar and limits are unconditional; only the HTTP edge (interceptor, error mapping through the
 * web problem chain) needs a servlet application.
 */
@AutoConfiguration
@Import({GraphQlProperties.class, GraphQlPlatformAutoConfiguration.WebEdge.class})
public class GraphQlPlatformAutoConfiguration {
  static final String QUERY_TYPE = "Query";
  static final String PING_FIELD = "ping";
  static final String PING_ANSWER = "PONG";

  /** Rejects deep queries before execution (graphql-java's own check, plan 07 A3.3). */
  @Bean
  MaxQueryDepthInstrumentation maxQueryDepthInstrumentation(GraphQlProperties properties) {
    return new MaxQueryDepthInstrumentation(properties.maxDepth());
  }

  /** Rejects wide queries before execution; every field costs 1. */
  @Bean
  MaxQueryComplexityInstrumentation maxQueryComplexityInstrumentation(
      GraphQlProperties properties) {
    return new MaxQueryComplexityInstrumentation(properties.maxComplexity());
  }

  @Bean
  RuntimeWiringConfigurer trellisPlatformWiring() {
    return wiring ->
        wiring
            .scalar(DateTimeScalar.create())
            .type(QUERY_TYPE, type -> type.dataFetcher(PING_FIELD, env -> PING_ANSWER));
  }

  /**
   * HTTP edge of GraphQL: only in a servlet application, where the platform's request context
   * exists.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  static class WebEdge {
    @Bean
    RequestContextGraphQlInterceptor requestContextGraphQlInterceptor() {
      return new RequestContextGraphQlInterceptor();
    }

    @Bean
    TrellisGraphQlExceptionResolver trellisGraphQlExceptionResolver(ProblemFactory problems) {
      return new TrellisGraphQlExceptionResolver(problems);
    }
  }
}
