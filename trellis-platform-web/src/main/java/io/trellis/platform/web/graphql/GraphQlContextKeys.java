package io.trellis.platform.web.graphql;

/** Keys of values the platform puts into the GraphQL context. */
public final class GraphQlContextKeys {
  /**
   * The operation's {@code io.trellis.common.context.RequestContext}. Controllers take it as
   * {@code @ContextValue(GraphQlContextKeys.REQUEST_CONTEXT) RequestContext ctx} and pass it to the
   * application service.
   */
  public static final String REQUEST_CONTEXT = "trellisRequestContext";

  private GraphQlContextKeys() {}
}
