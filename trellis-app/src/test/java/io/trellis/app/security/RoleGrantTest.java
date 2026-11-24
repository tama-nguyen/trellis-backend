package io.trellis.app.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.trellis.app.support.IdentityFixtures;
import io.trellis.app.support.RoleFixtures;
import io.trellis.app.support.SecurityTestContext;
import io.trellis.common.context.Actor;
import io.trellis.common.security.Permission;
import io.trellis.common.security.ResourceScope;
import io.trellis.learningcontent.api.security.CoursePermissions;
import io.trellis.platform.security.authz.GrantSubject;
import io.trellis.platform.security.authz.GrantSubjectType;
import io.trellis.platform.security.authz.RoleGrantStore;
import io.trellis.platform.security.authz.StoredPermissionResolver;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.junit.jupiter.api.Test;

@SecurityTestContext
@RequiredArgsConstructor
class RoleGrantTest {
  private static final Duration GRANT_TTL = Duration.ofMinutes(1);
  private static final long CONCURRENCY_TIMEOUT_SECONDS = 10;
  private final RoleGrantStore grants;
  private final IdentityFixtures transactions;
  private final RoleFixtures fixtures;
  private final Clock clock;

  @Test
  void tenantRolesNeverExpandThroughTokenOrStoredGrant() {
    // Given
    val subject = UUID.randomUUID().toString();
    val role = fixtures.tenantRole(subject);
    val grantSubject = new GrantSubject(GrantSubjectType.USER, subject, clock.instant());

    // When
    val rolePermissions = grants.permissionsForRoles(Set.of(role));
    val storedPermissions = grants.permissionsForSubject(grantSubject, ResourceScope.global());

    // Then
    assertTrue(rolePermissions.isEmpty());
    assertTrue(storedPermissions.isEmpty());
  }

  @Test
  void committedRevokeIsVisibleToIndependentResolversWithoutTtl() {
    // Given
    val subject = UUID.randomUUID().toString();
    val actor = Actor.user(subject, Set.of(), Set.of());
    val grant = fixtures.globalReader(subject);
    val first = resolverAt(clock);
    val second = resolverAt(clock);
    val firstBeforeRevoke = first.permissionsOf(actor, ResourceScope.global());
    val secondBeforeRevoke = second.permissionsOf(actor, ResourceScope.global());

    // When
    transactions.tx().executeWithoutResult(status -> grants.revoke(grant.id()));

    // Then
    assertFalse(firstBeforeRevoke.isEmpty());
    assertFalse(secondBeforeRevoke.isEmpty());
    assertTrue(first.permissionsOf(actor, ResourceScope.global()).isEmpty());
    assertTrue(second.permissionsOf(actor, ResourceScope.global()).isEmpty());
  }

  @Test
  void shouldKeepGrantWhenRevokeRollsBack() {
    // Given
    val subject = UUID.randomUUID().toString();
    val actor = Actor.user(subject, Set.of(), Set.of());
    val grant = fixtures.globalReader(subject);
    val resolver = resolverAt(clock);

    // When
    val failure = assertThrows(IllegalStateException.class, () -> revokeAndRollback(grant.id()));

    // Then
    assertEquals("rollback", failure.getMessage());
    assertFalse(resolver.permissionsOf(actor, ResourceScope.global()).isEmpty());
  }

  @Test
  void shouldExposeRevocationOnlyAfterCommit() throws Exception {
    // Given
    val subject = UUID.randomUUID().toString();
    val actor = Actor.user(subject, Set.of(), Set.of());
    val grant = fixtures.globalReader(subject);
    val resolver = resolverAt(clock);
    val deleted = new CountDownLatch(1);
    val commit = new CountDownLatch(1);

    try (val executor = Executors.newSingleThreadExecutor()) {
      // When
      val revoked = executor.submit(() -> revokeAndAwaitCommit(grant.id(), deleted, commit));
      val beforeCommit = permissionsBeforeCommit(resolver, actor, deleted, commit);
      revoked.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      val afterCommit = resolver.permissionsOf(actor, ResourceScope.global());

      // Then
      assertFalse(beforeCommit.isEmpty());
      assertTrue(afterCommit.isEmpty());
    }
  }

  @Test
  void shouldExpireStoredGrantAtItsExpiryInstant() {
    // Given
    val now = clock.instant();
    val expiry = now.plus(GRANT_TTL);
    val subject = UUID.randomUUID().toString();
    fixtures.readerUntil(subject, expiry);
    val actor = Actor.user(subject, Set.of(), Set.of());

    // When
    val beforeExpiry =
        resolverAt(Clock.fixed(now, ZoneOffset.UTC)).permissionsOf(actor, ResourceScope.global());
    val atExpiry =
        resolverAt(Clock.fixed(expiry, ZoneOffset.UTC))
            .permissionsOf(actor, ResourceScope.global());

    // Then
    assertFalse(beforeExpiry.isEmpty());
    assertTrue(atExpiry.isEmpty());
  }

  @Test
  void revocationPreservesIndependentTokenRights() {
    // Given
    val subject = UUID.randomUUID().toString();
    val grant = fixtures.globalReader(subject);
    val actor =
        Actor.user(
            subject,
            Set.of(RoleFixtures.READER_ROLE),
            Set.of(Permission.of(CoursePermissions.MANAGE)));
    val resolver = resolverAt(clock);

    // When
    transactions.tx().executeWithoutResult(status -> grants.revoke(grant.id()));
    val effective = resolver.permissionsOf(actor, ResourceScope.global());

    // Then
    assertTrue(effective.contains(Permission.of(CoursePermissions.READ)));
    assertTrue(effective.contains(Permission.of(CoursePermissions.MANAGE)));
  }

  private void revokeAndRollback(final long grantId) {
    transactions
        .tx()
        .executeWithoutResult(
            status -> {
              grants.revoke(grantId);
              throw new IllegalStateException("rollback");
            });
  }

  private void revokeAndAwaitCommit(
      final long grantId, final CountDownLatch deleted, final CountDownLatch commit) {
    transactions
        .tx()
        .executeWithoutResult(
            status -> {
              grants.revoke(grantId);
              deleted.countDown();
              await(commit);
            });
  }

  private Set<Permission> permissionsBeforeCommit(
      final StoredPermissionResolver resolver,
      final Actor actor,
      final CountDownLatch deleted,
      final CountDownLatch commit) {
    try {
      await(deleted);
      return resolver.permissionsOf(actor, ResourceScope.global());
    } finally {
      commit.countDown();
    }
  }

  private void await(final CountDownLatch latch) {
    try {
      assertTrue(
          latch.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS),
          "transaction synchronization timed out");
    } catch (final InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private StoredPermissionResolver resolverAt(final Clock decisionTime) {
    return new StoredPermissionResolver(grants, decisionTime);
  }
}
