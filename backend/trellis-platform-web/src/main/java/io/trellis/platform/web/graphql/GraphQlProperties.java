package io.trellis.platform.web.graphql;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * GraphQL cost limits. Introspection and GraphiQL are Spring Boot keys ({@code spring.graphql.*}), on in the
 * {@code dev} profile only.
 *
 * @param maxDepth deepest selection nesting a query may have; rejected before execution
 * @param maxComplexity total field count (each field costs 1) a query may select; rejected before execution
 */
@ConfigurationProperties(prefix = "trellis.graphql")
public record GraphQlProperties(
        @DefaultValue("15") int maxDepth,
        @DefaultValue("200") int maxComplexity) {}
