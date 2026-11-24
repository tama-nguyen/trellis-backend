package io.trellis.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micrometer.core.instrument.MeterRegistry;
import io.trellis.app.fixture.application.OrderProbeService;
import io.trellis.common.context.Actor;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.ForbiddenException;
import io.trellis.common.security.AuthorizationService;
import io.trellis.common.security.Permission;
import io.trellis.common.security.PlatformPermissions;
import io.trellis.platform.aspect.audit.AuditRecorder;
import io.trellis.platform.aspect.audit.AuditedAspect;
import io.trellis.platform.aspect.audit.JdbcAuditRecorder;
import io.trellis.platform.aspect.authz.RequiresPermissionAspect;
import io.trellis.platform.aspect.feature.RequiresFeatureAspect;
import io.trellis.platform.aspect.idempotency.IdempotencyStore;
import io.trellis.platform.aspect.idempotency.IdempotentAspect;
import io.trellis.platform.aspect.idempotency.JdbcIdempotencyStore;
import io.trellis.platform.aspect.lock.DistributedLockAspect;
import io.trellis.platform.aspect.lock.LockProvider;
import io.trellis.platform.aspect.lock.PostgresAdvisoryLockProvider;
import io.trellis.platform.aspect.logging.LogExecutionAspect;
import io.trellis.platform.aspect.ratelimit.RateLimitedAspect;
import io.trellis.platform.security.authz.DefaultAuthorizationService;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.aopalliance.aop.Advice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AbstractAspectJAdvice;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Proves the advice order documented in {@code AspectOrder} is the order that actually runs: first
 * by reading the proxy's advisor chain, then by journaling what each collaborator sees during a
 * real call (including whether a transaction is active at that point).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresTestcontainer.class, AspectOrderingIT.Recording.class})
class AspectOrderingIT {
  private static final String ADMIN_ROLE = "platform-admin";

  @Autowired private OrderProbeService probe;

  @Autowired private OrderProbeService.Journal journal;

  @Autowired private JdbcClient jdbc;

  @Autowired private Clock clock;

  @BeforeEach
  void clearJournal() {
    journal.clear();
  }

  @Test
  void advisorChainFollowsAspectOrder() {
    List<Class<?>> chain =
        Arrays.stream(((Advised) probe).getAdvisors())
            .map(Advisor::getAdvice)
            .map(AspectOrderingIT::adviceType)
            .filter(type -> type != null)
            .toList();
    assertEquals(
        List.of(
            LogExecutionAspect.class,
            RequiresFeatureAspect.class,
            RateLimitedAspect.class,
            RequiresPermissionAspect.class,
            IdempotentAspect.class,
            DistributedLockAspect.class,
            TransactionInterceptor.class,
            AuditedAspect.class),
        chain);
  }

  @Test
  void collaboratorsRunInOrderWithTransactionOnlyAroundMethodAndAudit() {
    String key = UUID.randomUUID().toString();
    probe.probe(admin(), key);
    assertEquals(
        List.of(
            "authorize(tx=false)",
            "idempotency.claim(tx=false)",
            "lock.acquire(tx=false)",
            "method(tx=true)",
            "audit(tx=true)",
            "lock.release(tx=false)",
            "idempotency.complete(tx=false)"),
        journal);
    assertEquals(1, auditRows(key));
  }

  @Test
  void replayReturnsStoredResultWithoutRunningTheMethodAgain() {
    String key = UUID.randomUUID().toString();
    OrderProbeService.ProbeResult first = probe.probe(admin(), key);
    journal.clear();
    OrderProbeService.ProbeResult replay = probe.probe(admin(), key);
    assertEquals(first, replay);
    assertEquals(List.of("authorize(tx=false)", "idempotency.claim(tx=false)"), journal);
    assertEquals(1, auditRows(key));
  }

  @Test
  void deniedCallerNeverReachesIdempotencyLockOrMethod() {
    String key = UUID.randomUUID().toString();
    RequestContext outsider = admin().withActor(Actor.user("outsider", Set.of(), Set.of()));
    assertThrows(ForbiddenException.class, () -> probe.probe(outsider, key));
    assertEquals(List.of("authorize(tx=false)"), journal);
    assertEquals(0, auditRows(key));
  }

  private RequestContext admin() {
    return RequestContext.builder()
        .actor(Actor.user("admin", Set.of(ADMIN_ROLE), Set.of()))
        .correlationId(UUID.randomUUID().toString())
        .requestTime(clock.instant())
        .build();
  }

  private long auditRows(String key) {
    return jdbc.sql(
            "SELECT count(*) FROM audit_event WHERE action = :action AND aggregate_id = :key")
        .param("action", OrderProbeService.ACTION)
        .param("key", key)
        .query(Long.class)
        .single();
  }

  private static Class<?> adviceType(Advice advice) {
    if (advice instanceof AbstractAspectJAdvice aspectAdvice) {
      return aspectAdvice.getAspectJAdviceMethod().getDeclaringClass();
    }
    return advice instanceof TransactionInterceptor ? TransactionInterceptor.class : null;
  }

  private static String tx(String step) {
    return step + "(tx=" + TransactionSynchronizationManager.isActualTransactionActive() + ")";
  }

  /** Real platform collaborators wrapped to journal each call. */
  @TestConfiguration(proxyBeanMethods = false)
  static class Recording {
    @Bean
    OrderProbeService.Journal aspectJournal() {
      return new OrderProbeService.Journal();
    }

    @Bean
    OrderProbeService orderProbeService(OrderProbeService.Journal aspectJournal) {
      return new OrderProbeService(aspectJournal);
    }

    @Bean
    AuthorizationService recordingAuthorizationService(
        OrderProbeService.Journal journal, MeterRegistry meters) {
      AuthorizationService real =
          new DefaultAuthorizationService(
              (actor, scope) ->
                  actor.roles().contains(ADMIN_ROLE)
                      ? Set.of(Permission.of(PlatformPermissions.EVENTS_REPLAY))
                      : Set.of(),
              meters);
      return (ctx, requirement, scope) -> {
        journal.add(tx("authorize"));
        return real.decide(ctx, requirement, scope);
      };
    }

    @Bean
    IdempotencyStore recordingIdempotencyStore(
        OrderProbeService.Journal journal,
        JdbcClient jdbc,
        PlatformTransactionManager tm,
        Clock clock) {
      IdempotencyStore real = new JdbcIdempotencyStore(jdbc, tm, clock);
      return new IdempotencyStore() {
        @Override
        public Claim claim(String scope, String key, Duration timeout) {
          journal.add(tx("idempotency.claim"));
          return real.claim(scope, key, timeout);
        }

        @Override
        public void complete(String scope, String key, String resultJson, Duration ttl) {
          journal.add(tx("idempotency.complete"));
          real.complete(scope, key, resultJson, ttl);
        }

        @Override
        public void release(String scope, String key) {
          journal.add(tx("idempotency.release"));
          real.release(scope, key);
        }
      };
    }

    @Bean
    LockProvider recordingLockProvider(
        OrderProbeService.Journal journal, DataSource dataSource, Clock clock) {
      LockProvider real =
          new PostgresAdvisoryLockProvider(dataSource, Duration.ofMillis(50), clock);
      return (name, waitFor) -> {
        journal.add(tx("lock.acquire"));
        Optional<LockProvider.Handle> handle = real.tryLock(name, waitFor);
        return handle.map(
            h ->
                () -> {
                  journal.add(tx("lock.release"));
                  h.close();
                });
      };
    }

    @Bean
    AuditRecorder recordingAuditRecorder(OrderProbeService.Journal journal, JdbcClient jdbc) {
      AuditRecorder real = new JdbcAuditRecorder(jdbc);
      return entry -> {
        journal.add(tx("audit"));
        real.record(entry);
      };
    }
  }
}
