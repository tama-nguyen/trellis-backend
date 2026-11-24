package io.trellis.platform.security.authz;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.trellis.common.security.Permission;
import io.trellis.common.security.ResourceScope;
import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Short-lived cache of the permissions a decision needs, so one request does not query the same
 * mapping several times. The time-to-live bounds how long a revoked grant can still be honoured; it
 * is deliberately short rather than invalidated across instances, because an in-process
 * invalidation cannot reach the other replicas.
 *
 * <p>The key never includes the instant a decision is made: that changes every request and would
 * make every lookup a miss. Expiry is applied by the query that loads the entry.
 */
public final class GrantCache {
  private final Cache<Object, Set<Permission>> cache;

  public GrantCache(Duration ttl, long maxSize) {
    this.cache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build();
  }

  /** Permissions a subject holds at one scope. */
  public Set<Permission> bySubject(
      GrantSubject subject, ResourceScope scope, Supplier<Set<Permission>> loader) {
    return cache.get(
        new SubjectKey(subject.type(), subject.id(), scope), ignored -> Set.copyOf(loader.get()));
  }

  /**
   * Permissions a set of token roles maps to; the hottest lookup, since every decision makes it.
   */
  public Set<Permission> byRoles(Set<String> roles, Supplier<Set<Permission>> loader) {
    return cache.get(new RolesKey(Set.copyOf(roles)), ignored -> Set.copyOf(loader.get()));
  }

  public void invalidateAll() {
    cache.invalidateAll();
  }

  private record SubjectKey(GrantSubjectType type, String id, ResourceScope scope) {}

  private record RolesKey(Set<String> roles) {}
}
