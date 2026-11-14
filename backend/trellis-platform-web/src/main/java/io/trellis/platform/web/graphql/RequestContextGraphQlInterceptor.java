package io.trellis.platform.web.graphql;

import io.trellis.common.context.AmbientRequestContext;
import io.trellis.common.context.RequestContext;
import io.trellis.platform.web.context.RequestContextFactory;
import java.util.Map;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import reactor.core.publisher.Mono;

/**
 * Puts the HTTP request's {@link RequestContext} (built once by the platform filter, after authentication) into
 * the GraphQL context, the way the REST argument resolver hands it to controllers. Resolvers never rebuild it and
 * never read the thread-bound copy themselves.
 */
public final class RequestContextGraphQlInterceptor implements WebGraphQlInterceptor {
    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        RequestContext ctx = RequestContextFactory.cached(request.getAttributes())
                .or(AmbientRequestContext::current)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "No RequestContext for the GraphQL request: is the platform request-context filter registered?"));
        request.configureExecutionInput(
                (input, builder) -> builder.graphQLContext(Map.of(GraphQlContextKeys.REQUEST_CONTEXT, ctx))
                        .build());
        return chain.next(request);
    }
}
