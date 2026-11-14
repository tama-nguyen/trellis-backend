package io.trellis.platform.observability;

import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.tracing.Tracer;
import io.trellis.common.context.TraceContextAccessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Observability wiring. The {@link ContextPropagation} bean is a {@code TaskDecorator}, which Spring Boot applies
 * to the auto-configured {@code applicationTaskExecutor} (used by {@code @Async} and the platform), so async
 * work keeps trace, MDC and request context without any call-site code.
 */
@AutoConfiguration(after = MicrometerTracingAutoConfiguration.class)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {
    private static final String HTTP_SERVER_REQUESTS = "http.server.requests";
    private static final String URI_TAG = "uri";

    @Bean
    @ConditionalOnBean(Tracer.class)
    @ConditionalOnMissingBean(TraceContextAccessor.class)
    TraceContextAccessor traceContextAccessor(Tracer tracer) {
        return new MicrometerTraceContextAccessor(tracer);
    }

    @Bean
    @ConditionalOnMissingBean
    ContextPropagation contextPropagation() {
        return new ContextPropagation();
    }

    @Bean
    MeterFilter uriCardinalityGuard(ObservabilityProperties properties) {
        return MeterFilter.maximumAllowableTags(
                HTTP_SERVER_REQUESTS, URI_TAG, properties.maxUriTags(), MeterFilter.deny());
    }
}
