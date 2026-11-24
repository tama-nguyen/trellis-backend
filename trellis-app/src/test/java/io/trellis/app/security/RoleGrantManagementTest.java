package io.trellis.app.security;

import static io.trellis.app.support.TestJwtIssuer.jwt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.trellis.app.platform.dto.rolegrant.RoleGrantRequest;
import io.trellis.app.support.IdentityFixtures;
import io.trellis.app.support.RoleFixtures;
import io.trellis.app.support.SecurityTestContext;
import io.trellis.common.security.ResourceScope;
import io.trellis.identity.domain.user.UserStatus;
import io.trellis.platform.audit.AuditEventFilter;
import io.trellis.platform.audit.AuditEventStore;
import io.trellis.platform.security.authz.GrantSubjectType;
import io.trellis.platform.security.authz.RoleGrant;
import io.trellis.platform.security.authz.RoleGrantFilter;
import io.trellis.platform.security.authz.RoleGrantStore;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

@SecurityTestContext
@Import(RoleGrantApi.class)
@RequiredArgsConstructor
class RoleGrantManagementTest {

  @LocalServerPort private int port;

  private final RoleGrantApi api;
  private final IdentityFixtures identities;
  private final RoleFixtures fixtures;
  private final RoleGrantStore grants;
  private final AuditEventStore auditEvents;

  @Test
  void readerCannotReusePrivilegedIdempotencyKey() throws Exception {
    // Given
    val request = readerGrantRequest();
    val key = UUID.randomUUID().toString();
    val original = api.grant(port, administrator(), key, request);
    assertEquals(
        HttpStatus.CREATED.value(),
        original.statusCode(),
        "privileged request must create the fixture grant");

    // When
    val response = api.grant(port, reader(), key, request);

    // Then
    assertEquals(HttpStatus.FORBIDDEN.value(), response.statusCode());
    val grantId = api.body(original).id();
    val audit =
        auditEvents.page(
            new AuditEventFilter("RoleGrant", Long.toString(grantId), null, null, null), null, 50);
    assertFalse(audit.hasNext());
    assertEquals(1, audit.items().size());
  }

  @Test
  void administratorCreatesAuditedGrant() throws Exception {
    // Given
    val request = readerGrantRequest();

    // When
    val response = api.grant(port, administrator(), UUID.randomUUID().toString(), request);

    // Then
    assertEquals(HttpStatus.CREATED.value(), response.statusCode());
    val created = api.body(response);
    val stored = grants.page(filter(request.subjectId()), null, 50);
    assertFalse(stored.hasNext());
    assertEquals(List.of(created.id()), stored.items().stream().map(RoleGrant::id).toList());
    val audit =
        auditEvents.page(
            new AuditEventFilter("RoleGrant", Long.toString(created.id()), null, null, null),
            null,
            50);
    assertFalse(audit.hasNext());
    assertEquals(
        1L,
        audit.items().stream()
            .filter(event -> "platform.role-grant.granted".equals(event.action()))
            .count());
  }

  @Test
  void replayDoesNotDuplicateGrantOrAudit() throws Exception {
    // Given
    val request = readerGrantRequest();
    val actor = administrator();
    val key = UUID.randomUUID().toString();
    val original = api.grant(port, actor, key, request);
    assertEquals(
        HttpStatus.CREATED.value(), original.statusCode(), "first request must create the grant");
    val first = api.body(original);

    // When
    val replay = api.grant(port, actor, key, request);

    // Then
    assertEquals(HttpStatus.CREATED.value(), replay.statusCode());
    assertEquals(first, api.body(replay));
    val audit =
        auditEvents.page(
            new AuditEventFilter("RoleGrant", Long.toString(first.id()), null, null, null),
            null,
            50);
    assertFalse(audit.hasNext());
    assertEquals(1, audit.items().size());
  }

  @Test
  void revokeRemovesStoredGrant() throws Exception {
    // Given
    val subject = UUID.randomUUID().toString();
    val granted = fixtures.globalReader(subject);
    assertEquals(granted.id(), grants.page(filter(subject), null, 50).items().getFirst().id());

    // When
    val response = api.revoke(port, administrator(), granted.id());

    // Then
    assertEquals(HttpStatus.NO_CONTENT.value(), response.statusCode());
    val remaining = grants.page(filter(subject), null, 50);
    assertFalse(remaining.hasNext());
    assertTrue(remaining.items().isEmpty());
  }

  @Test
  void repeatedRevocationReturnsNotFound() throws Exception {
    // Given
    val granted = fixtures.globalReader(UUID.randomUUID().toString());
    val actor = administrator();
    assertEquals(
        HttpStatus.NO_CONTENT.value(),
        api.revoke(port, actor, granted.id()).statusCode(),
        "first revoke must remove the grant");

    // When
    val response = api.revoke(port, actor, granted.id());

    // Then
    assertEquals(HttpStatus.NOT_FOUND.value(), response.statusCode());
  }

  private RoleGrantRequest readerGrantRequest() {
    return new RoleGrantRequest(
        GrantSubjectType.USER,
        identities.nativeUser(UserStatus.ACTIVE).toString(),
        RoleFixtures.READER_ROLE,
        ResourceScope.GLOBAL,
        ResourceScope.GLOBAL,
        null);
  }

  private RoleGrantFilter filter(final String subject) {
    return new RoleGrantFilter(
        GrantSubjectType.USER,
        subject,
        RoleFixtures.READER_ROLE,
        ResourceScope.GLOBAL,
        ResourceScope.GLOBAL);
  }

  private String administrator() {
    return jwt(UUID.randomUUID().toString(), RoleFixtures.ADMINISTRATOR_ROLE);
  }

  private String reader() {
    return jwt(UUID.randomUUID().toString(), RoleFixtures.READER_ROLE);
  }
}
