package io.trellis.platform.web;

import io.trellis.common.context.Actor;
import io.trellis.common.context.TraceContextAccessor;
import io.trellis.platform.security.ActorResolver;
import io.trellis.platform.web.context.RequestContextArgumentResolver;
import io.trellis.platform.web.context.RequestContextFactory;
import io.trellis.platform.web.error.FrameworkProblemMapper;
import io.trellis.platform.web.error.GlobalExceptionHandler;
import io.trellis.platform.web.error.ProblemErrorController;
import io.trellis.platform.web.error.ProblemFactory;
import io.trellis.platform.web.error.ProblemMapper;
import io.trellis.platform.web.error.ProblemResponseWriter;
import io.trellis.platform.web.error.TrellisProblemMapper;
import io.trellis.platform.web.filter.AmbientRequestContextFilter;
import io.trellis.platform.web.filter.CorrelationIdFilter;
import io.trellis.platform.web.filter.RequestLoggingFilter;
import java.time.Clock;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

/** HTTP edge wiring. Mappers are ordered: platform types first, framework types last. */
@AutoConfiguration(before = ErrorMvcAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(WebProperties.class)
public class WebAutoConfiguration {
    /** Mapper order slots; other modules pick values between them. */
    public static final int TRELLIS_MAPPER_ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    public static final int FRAMEWORK_MAPPER_ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @Order(TRELLIS_MAPPER_ORDER)
    TrellisProblemMapper trellisProblemMapper() {
        return new TrellisProblemMapper();
    }

    @Bean
    @Order(FRAMEWORK_MAPPER_ORDER)
    FrameworkProblemMapper frameworkProblemMapper() {
        return new FrameworkProblemMapper();
    }

    @Bean
    ProblemFactory problemFactory(List<ProblemMapper> mappers, ObjectProvider<TraceContextAccessor> trace) {
        return new ProblemFactory(mappers, trace.getIfAvailable(() -> TraceContextAccessor.NOOP));
    }

    @Bean
    GlobalExceptionHandler globalExceptionHandler(ProblemFactory problems) {
        return new GlobalExceptionHandler(problems);
    }

    @Bean
    ProblemErrorController problemErrorController(ProblemFactory problems) {
        return new ProblemErrorController(problems);
    }

    @Bean
    ProblemResponseWriter problemResponseWriter(ProblemFactory problems, JsonMapper json) {
        return new ProblemResponseWriter(problems, json);
    }

    @Bean
    @ConditionalOnMissingBean
    ActorResolver anonymousActorResolver() {
        return Actor::anonymous;
    }

    @Bean
    RequestContextFactory requestContextFactory(
            ActorResolver actors, ObjectProvider<TraceContextAccessor> trace, Clock clock) {
        return new RequestContextFactory(actors, trace.getIfAvailable(() -> TraceContextAccessor.NOOP), clock);
    }

    @Bean
    WebMvcConfigurer requestContextArgumentResolverConfigurer(RequestContextFactory factory) {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new RequestContextArgumentResolver(factory));
            }
        };
    }

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter(WebProperties properties, Clock clock) {
        var registration = new FilterRegistrationBean<>(
                new CorrelationIdFilter(Pattern.compile(properties.correlationIdPattern()), clock));
        registration.setOrder(WebFilterOrder.CORRELATION_ID);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "trellis.web", name = "request-log", havingValue = "true", matchIfMissing = true)
    FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        var registration = new FilterRegistrationBean<>(new RequestLoggingFilter());
        registration.setOrder(WebFilterOrder.REQUEST_LOG);
        return registration;
    }

    @Bean
    FilterRegistrationBean<AmbientRequestContextFilter> ambientRequestContextFilter(RequestContextFactory factory) {
        var registration = new FilterRegistrationBean<>(new AmbientRequestContextFilter(factory));
        registration.setOrder(WebFilterOrder.REQUEST_CONTEXT);
        return registration;
    }
}
