package io.trellis.platform.web.graphql;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;

/**
 * GraphQL cost limits. Introspection and GraphiQL are Spring Boot keys ({@code spring.graphql.*}),
 * on in the {@code dev} profile only.
 *
 * <p>maxDepth: deepest selection nesting a query may have; rejected before execution maxComplexity:
 * total field count (each field costs 1) a query may select; rejected before execution
 */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public final class GraphQlProperties {
  @Value("${trellis.graphql.max-depth:15}")
  private int maxDepth;

  @Value("${trellis.graphql.max-complexity:200}")
  private int maxComplexity;
}
