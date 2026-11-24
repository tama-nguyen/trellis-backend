package io.trellis.platform.aspect;

import io.micrometer.core.instrument.MeterRegistry;
import io.trellis.common.page.CursorCodec;
import io.trellis.common.security.AuthorizationDecision;
import io.trellis.common.security.AuthorizationService;
import io.trellis.common.security.PermissionRegistry;
import io.trellis.platform.aspect.audit.AuditRecorder;
import io.trellis.platform.aspect.audit.AuditedAspect;
import io.trellis.platform.aspect.audit.JdbcAuditRecorder;
import io.trellis.platform.aspect.authz.PermissionUsageVerifier;
import io.trellis.platform.aspect.authz.RequiresPermissionAspect;
import io.trellis.platform.aspect.feature.FeatureFlagSource;
import io.trellis.platform.aspect.feature.FeatureFlagUsageVerifier;
import io.trellis.platform.aspect.feature.RequiresFeatureAspect;
import io.trellis.platform.aspect.idempotency.IdempotencyProperties;
import io.trellis.platform.aspect.idempotency.IdempotencyStore;
import io.trellis.platform.aspect.idempotency.IdempotentAspect;
import io.trellis.platform.aspect.idempotency.JdbcIdempotencyStore;
import io.trellis.platform.aspect.lock.DistributedLockAspect;
import io.trellis.platform.aspect.lock.LockProperties;
import io.trellis.platform.aspect.lock.LockProvider;
import io.trellis.platform.aspect.lock.PostgresAdvisoryLockProvider;
import io.trellis.platform.aspect.logging.LogExecutionAspect;
import io.trellis.platform.aspect.ratelimit.LocalRateLimiterStore;
import io.trellis.platform.aspect.ratelimit.RateLimitProperties;
import io.trellis.platform.aspect.ratelimit.RateLimitedAspect;
import io.trellis.platform.aspect.ratelimit.RateLimiterStore;
import io.trellis.platform.aspect.ratelimit.RedisRateLimiterStore;
import io.trellis.platform.aspect.support.SpelKeyEvaluator;
import io.trellis.platform.audit.AuditEventStore;
import io.trellis.platform.audit.JdbcAuditEventStore;
import java.time.Clock;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registers the platform aspects and pins {@code @Transactional}'s advice order to {@link
 * AspectOrder#TRANSACTION} (Spring Boot's own {@code @EnableTransactionManagement} backs off
 * because this one is processed first). Missing collaborators fail closed: no {@code
 * AuthorizationService} denies everything, no {@code FeatureFlagSource} keeps every toggle off.
 */
@AutoConfiguration(
    before = TransactionAutoConfiguration.class,
    afterName = "io.trellis.platform.security.SecurityAutoConfiguration")
@EnableTransactionManagement(proxyTargetClass = true, order = AspectOrder.TRANSACTION)
@Import({IdempotencyProperties.class, RateLimitProperties.class, LockProperties.class})
public class AspectAutoConfiguration {
  private static final AuthorizationService DENY_ALL =
      (ctx, requirement, scope) -> AuthorizationDecision.deny("no AuthorizationService configured");
  private static final FeatureFlagSource ALL_OFF =
      new FeatureFlagSource() {
        @Override
        public boolean isEnabled(String flag) {
          return false;
        }

        @Override
        public Set<String> flagNames() {
          return Set.of();
        }
      };

  @Bean
  SpelKeyEvaluator spelKeyEvaluator() {
    return new SpelKeyEvaluator();
  }

  @Bean
  LogExecutionAspect logExecutionAspect() {
    return new LogExecutionAspect();
  }

  @Bean
  RequiresFeatureAspect requiresFeatureAspect(ObjectProvider<FeatureFlagSource> flags) {
    return new RequiresFeatureAspect(new LazyFlags(flags));
  }

  @Bean
  FeatureFlagUsageVerifier featureFlagUsageVerifier(
      ListableBeanFactory beans, ObjectProvider<FeatureFlagSource> flags) {
    return new FeatureFlagUsageVerifier(beans, new LazyFlags(flags));
  }

  @Bean
  @ConditionalOnMissingBean
  RateLimiterStore rateLimiterStore(RateLimitProperties properties) {
    return RateLimitProperties.STORE_REDIS.equals(properties.store())
        ? new RedisRateLimiterStore(properties.redis().uri())
        : new LocalRateLimiterStore(properties.cacheSize());
  }

  @Bean
  RateLimitedAspect rateLimitedAspect(RateLimiterStore store, SpelKeyEvaluator spel, Clock clock) {
    return new RateLimitedAspect(store, spel, clock);
  }

  @Bean
  RequiresPermissionAspect requiresPermissionAspect(
      ObjectProvider<AuthorizationService> authorization, Clock clock) {
    return new RequiresPermissionAspect(
        (ctx, requirement, scope) ->
            authorization.getIfAvailable(() -> DENY_ALL).decide(ctx, requirement, scope),
        clock);
  }

  @Bean
  @ConditionalOnBean(PermissionRegistry.class)
  PermissionUsageVerifier permissionUsageVerifier(
      ListableBeanFactory beans, PermissionRegistry known) {
    return new PermissionUsageVerifier(beans, known);
  }

  @Bean
  @ConditionalOnMissingBean
  IdempotencyStore idempotencyStore(
      JdbcClient jdbc, PlatformTransactionManager transactionManager, Clock clock) {
    return new JdbcIdempotencyStore(jdbc, transactionManager, clock);
  }

  @Bean
  IdempotentAspect idempotentAspect(
      IdempotencyStore store,
      SpelKeyEvaluator spel,
      JsonMapper json,
      IdempotencyProperties properties) {
    return new IdempotentAspect(store, spel, json, properties);
  }

  @Bean
  @ConditionalOnMissingBean
  LockProvider lockProvider(DataSource dataSource, LockProperties properties, Clock clock) {
    return new PostgresAdvisoryLockProvider(dataSource, properties.pollInterval(), clock);
  }

  @Bean
  DistributedLockAspect distributedLockAspect(
      LockProvider locks, SpelKeyEvaluator spel, MeterRegistry meters) {
    return new DistributedLockAspect(locks, spel, meters);
  }

  @Bean
  @ConditionalOnMissingBean
  AuditRecorder auditRecorder(JdbcClient jdbc) {
    return new JdbcAuditRecorder(jdbc);
  }

  @Bean
  @ConditionalOnMissingBean
  AuditEventStore auditEventStore(JdbcClient jdbc, CursorCodec cursors) {
    return new JdbcAuditEventStore(jdbc, cursors);
  }

  @Bean
  AuditedAspect auditedAspect(AuditRecorder recorder, SpelKeyEvaluator spel, Clock clock) {
    return new AuditedAspect(recorder, spel, clock);
  }

  /** Resolves the flag source on use, so the app's FeatureFlags bean may be defined anywhere. */
  private record LazyFlags(ObjectProvider<FeatureFlagSource> provider)
      implements FeatureFlagSource {
    @Override
    public boolean isEnabled(String flag) {
      return provider.getIfAvailable(() -> ALL_OFF).isEnabled(flag);
    }

    @Override
    public Set<String> flagNames() {
      return provider.getIfAvailable(() -> ALL_OFF).flagNames();
    }
  }
}
