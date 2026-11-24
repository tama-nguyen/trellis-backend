package io.trellis.common.context;

import io.trellis.common.id.UuidV7;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable context of one operation: actor, correlation id, trace id, locale, tenant and the time
 * the operation started. Changes are copy-on-write via the {@code withX} methods.
 *
 * <p>Pass it explicitly as the first parameter of application-service methods: {@code
 * notices.publish(ctx, command)}. Tests build one in a line with {@link #builder()} or {@link
 * #system}.
 *
 * @param actor who acts; never null (use {@link Actor#anonymous()})
 * @param correlationId request or job correlation id; never blank
 * @param traceId OpenTelemetry trace id, or null when tracing is off
 * @param locale preferred locale for messages
 * @param tenantId placeholder for multi-tenancy; null in single-tenant deployments
 * @param requestTime when the operation started; business code uses this instead of reading the
 *     clock again
 */
public record RequestContext(
    Actor actor,
    String correlationId,
    String traceId,
    Locale locale,
    String tenantId,
    Instant requestTime) {

  public RequestContext {
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(correlationId, "correlationId");
    if (correlationId.isBlank()) {
      throw new IllegalArgumentException("correlationId must not be blank");
    }
    Objects.requireNonNull(locale, "locale");
    Objects.requireNonNull(requestTime, "requestTime");
  }

  /** Context for work the application starts itself (scheduler, relay, migration). */
  public static RequestContext system(String component, Clock clock) {
    return builder()
        .actor(Actor.system(component))
        .correlationId(UuidV7.next(clock).toString())
        .requestTime(clock.instant())
        .build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder toBuilder() {
    return new Builder()
        .actor(actor)
        .correlationId(correlationId)
        .traceId(traceId)
        .locale(locale)
        .tenantId(tenantId)
        .requestTime(requestTime);
  }

  public Optional<String> findTraceId() {
    return Optional.ofNullable(traceId);
  }

  public Optional<String> findTenantId() {
    return Optional.ofNullable(tenantId);
  }

  public RequestContext withActor(Actor newActor) {
    return toBuilder().actor(newActor).build();
  }

  public RequestContext withCorrelationId(String newCorrelationId) {
    return toBuilder().correlationId(newCorrelationId).build();
  }

  public RequestContext withTraceId(String newTraceId) {
    return toBuilder().traceId(newTraceId).build();
  }

  public RequestContext withLocale(Locale newLocale) {
    return toBuilder().locale(newLocale).build();
  }

  public RequestContext withTenantId(String newTenantId) {
    return toBuilder().tenantId(newTenantId).build();
  }

  /** Mutable builder; {@link #build()} validates. */
  public static final class Builder {
    private Actor actor = Actor.anonymous();
    private String correlationId;
    private String traceId;
    private Locale locale = Locale.ENGLISH;
    private String tenantId;
    private Instant requestTime;

    private Builder() {}

    public Builder actor(Actor value) {
      this.actor = value;
      return this;
    }

    public Builder correlationId(String value) {
      this.correlationId = value;
      return this;
    }

    public Builder traceId(String value) {
      this.traceId = value;
      return this;
    }

    public Builder locale(Locale value) {
      this.locale = value;
      return this;
    }

    public Builder tenantId(String value) {
      this.tenantId = value;
      return this;
    }

    public Builder requestTime(Instant value) {
      this.requestTime = value;
      return this;
    }

    public RequestContext build() {
      return new RequestContext(actor, correlationId, traceId, locale, tenantId, requestTime);
    }
  }
}
