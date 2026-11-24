package io.trellis.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import io.trellis.platform.audit.AuditEventFilter;
import io.trellis.platform.audit.AuditEventStore;
import io.trellis.platform.security.authz.DefaultAuthorizationService;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.aopalliance.aop.Advice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AbstractAspectJAdvice;
import org.springframework.aop.framework.Advised;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
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
@Import({PostgresTestcontainer.class, AspectOrderingTest.Recording.class})
@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AspectOrderingTest {

  private static final String ADMIN_ROLE = "platform-admin";

  private final OrderProbeService probe;

  private final OrderProbeService.Journal journal;

  private final AuditEventStore auditEvents;

  private final Clock clock;

  @BeforeEach
  void clearJournal() {
    journal.clear();
  }

  @Test
  void advisorChainFollowsAspectOrder() {
    // When
    val chain =
        Arrays.stream(((Advised) probe).getAdvisors())
            .map(Advisor::getAdvice)
            .map(AspectOrderingTest::adviceType)
            .filter(type -> type != null)
            .toList();

    // Then
    assertEquals(
        List.of(
            LogExecutionAspect.class,
            RequiresFeatureAspect.class,
            RequiresPermissionAspect.class,
            IdempotentAspect.class,
            DistributedLockAspect.class,
            TransactionInterceptor.class,
            AuditedAspect.class),
        chain);
  }

  @Test
  void collaboratorsRunInOrderWithTransactionOnlyAroundMethodAndAudit() {
    // Given
    val key = UUID.randomUUID().toString();

    // When
    probe.probe(admin(), key);

    // Then
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
    // Given
    val key = UUID.randomUUID().toString();
    val first = probe.probe(admin(), key);
    journal.clear();

    // When
    val replay = probe.probe(admin(), key);

    // Then
    assertEquals(first, replay);
    assertEquals(List.of("authorize(tx=false)", "idempotency.claim(tx=false)"), journal);
    assertEquals(1, auditRows(key));
  }

  @Test
  void deniedCallerNeverReachesIdempotencyLockOrMethod() {
    // Given
    val key = UUID.randomUUID().toString();
    val outsider = admin().withActor(Actor.user("outsider", Set.of(), Set.of()));

    // When
    assertThrows(ForbiddenException.class, () -> probe.probe(outsider, key));

    // Then
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

  private long auditRows(final String key) {
    val page = auditEvents.page(new AuditEventFilter("Probe", key, null, null, null), null, 50);
    assertFalse(page.hasNext());
    return page.items().stream()
        .filter(event -> OrderProbeService.ACTION.equals(event.action()))
        .count();
  }

  private static Class<?> adviceType(final Advice advice) {
    if (advice instanceof AbstractAspectJAdvice aspectAdvice) {
      return aspectAdvice.getAspectJAdviceMethod().getDeclaringClass();
    }
    return advice instanceof TransactionInterceptor ? TransactionInterceptor.class : null;
  }

  private static String tx(final String step) {
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
    OrderProbeService orderProbeService(final OrderProbeService.Journal aspectJournal) {
      return new OrderProbeService(aspectJournal);
    }

    @Bean
    AuthorizationService recordingAuthorizationService(
        final OrderProbeService.Journal journal, final MeterRegistry meters) {
      val real =
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
        final OrderProbeService.Journal journal,
        final JdbcClient jdbc,
        final PlatformTransactionManager tm,
        final Clock clock) {
      val real = new JdbcIdempotencyStore(jdbc, tm, clock);
      return new IdempotencyStore() {
        @Override
        public Claim claim(final String scope, final String key, final Duration timeout) {
          journal.add(tx("idempotency.claim"));
          return real.claim(scope, key, timeout);
        }

        @Override
        public void complete(
            final String scope, final String key, final String resultJson, final Duration ttl) {
          journal.add(tx("idempotency.complete"));
          real.complete(scope, key, resultJson, ttl);
        }

        @Override
        public void release(final String scope, final String key) {
          journal.add(tx("idempotency.release"));
          real.release(scope, key);
        }
      };
    }

    @Bean
    LockProvider recordingLockProvider(
        final OrderProbeService.Journal journal, final DataSource dataSource, final Clock clock) {
      val real = new PostgresAdvisoryLockProvider(dataSource, Duration.ofMillis(50), clock);
      return (name, waitFor) -> {
        journal.add(tx("lock.acquire"));
        val handle = real.tryLock(name, waitFor);
        return handle.map(
            h ->
                () -> {
                  journal.add(tx("lock.release"));
                  h.close();
                });
      };
    }

    @Bean
    AuditRecorder recordingAuditRecorder(
        final OrderProbeService.Journal journal, final JdbcClient jdbc) {
      val real = new JdbcAuditRecorder(jdbc);
      return entry -> {
        journal.add(tx("audit"));
        real.record(entry);
      };
    }
  }
}
