package io.trellis.platform.web.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.ErrorCode;
import io.trellis.common.error.ProblemFields;
import io.trellis.platform.web.error.MappedProblem;
import io.trellis.platform.web.error.ProblemFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;

/**
 * Maps exceptions thrown below a resolver through the same {@code ProblemMapper} chain as REST, so a module adds
 * one error code and both APIs agree. The error carries {@code extensions.code} ({@link GraphQlErrorCode}),
 * {@code extensions.errorCode} (the module code), {@code correlationId}, {@code traceId} and, for validation,
 * {@code errors}. A server error never exposes its message or cause chain (CWE-209); the correlation id is how
 * support finds it in the logs. Other fields of the same query still resolve (partial result).
 */
public final class TrellisGraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {
    static final String EXTENSION_CODE = "code";
    static final String EXTENSION_ERROR_CODE = "errorCode";
    static final String GENERIC_SERVER_ERROR =
            "An unexpected error occurred. Quote the correlation id when reporting it.";

    private static final Logger log = LoggerFactory.getLogger(TrellisGraphQlExceptionResolver.class);

    private final ProblemFactory problems;

    public TrellisGraphQlExceptionResolver(ProblemFactory problems) {
        this.problems = problems;
    }

    @Override
    protected GraphQLError resolveToSingleError(Throwable thrown, DataFetchingEnvironment env) {
        Throwable error = unwrap(thrown);
        MappedProblem mapped = problems.resolve(error);
        ErrorCode code = mapped.errorCode();
        boolean serverError = code.category().isServerError();
        GraphQlErrorCode graphQlCode = GraphQlErrorCode.of(code.category());

        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put(EXTENSION_CODE, graphQlCode.name());
        extensions.put(EXTENSION_ERROR_CODE, code.code());
        RequestContext ctx = env.getGraphQlContext().get(GraphQlContextKeys.REQUEST_CONTEXT);
        if (ctx != null) {
            extensions.put(ProblemFields.CORRELATION_ID, ctx.correlationId());
            ctx.trace().ifPresent(trace -> extensions.put(ProblemFields.TRACE_ID, trace));
        }
        if (serverError) {
            log.atError()
                    .addKeyValue("error_code", code.code())
                    .addKeyValue("graphql_path", env.getExecutionStepInfo().getPath())
                    .setCause(error)
                    .log("GraphQL field failed");
        } else {
            mapped.properties().forEach(extensions::put);
        }
        String message = serverError || mapped.detail() == null ? defaultMessage(code, serverError) : mapped.detail();
        return GraphqlErrorBuilder.newError(env)
                .errorType(graphQlCode)
                .message(message)
                .extensions(extensions)
                .build();
    }

    private static String defaultMessage(ErrorCode code, boolean serverError) {
        return serverError ? GENERIC_SERVER_ERROR : code.title();
    }

    /** Batch loaders and async fetchers wrap the real exception; map the cause the service threw. */
    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
