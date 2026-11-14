/**
 * GraphQL platform: {@link io.trellis.platform.web.graphql.RequestContextGraphQlInterceptor} (the request's {@code
 * RequestContext} in the GraphQL context, read with {@code @ContextValue}), {@link
 * io.trellis.platform.web.graphql.TrellisGraphQlExceptionResolver} ({@code extensions.code}, correlation id, no root
 * causes), depth/complexity limits and the {@code DateTime} scalar. Schemas live in each module under {@code
 * src/main/resources/graphql/<module>/*.graphqls}.
 */
package io.trellis.platform.web.graphql;
