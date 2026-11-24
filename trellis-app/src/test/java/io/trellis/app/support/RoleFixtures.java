package io.trellis.app.support;

import io.trellis.common.security.ResourceScope;
import io.trellis.platform.security.authz.GrantSubjectType;
import io.trellis.platform.security.authz.RoleGrant;
import io.trellis.platform.security.authz.RoleGrantStore;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.jdbc.core.simple.JdbcClient;

@RequiredArgsConstructor
public class RoleFixtures {
  public static final String ADMINISTRATOR_ROLE = "platform-admin";
  public static final String READER_ROLE = "learner";
  private final JdbcClient jdbc;
  private final RoleGrantStore grants;
  private final IdentityFixtures transactions;
  private final Clock clock;

  public RoleGrant globalReader(final String subject) {
    return readerUntil(subject, null);
  }

  public RoleGrant readerUntil(final String subject, final Instant expiry) {
    return grant(subject, READER_ROLE, expiry);
  }

  public RoleGrant globalAdministrator(final String subject) {
    return grant(subject, ADMINISTRATOR_ROLE, null);
  }

  private RoleGrant grant(final String subject, final String role, final Instant expiry) {
    return transactions
        .tx()
        .execute(
            status ->
                grants.grant(
                    GrantSubjectType.USER,
                    subject,
                    role,
                    ResourceScope.global(),
                    clock.instant(),
                    "role-test-fixture",
                    expiry));
  }

  // No tenant-role creation port exists; deliberately bypass global-role lookup to seed this
  // boundary.
  public String tenantRole(final String subject) {
    val role = UUID.randomUUID();
    val name = "tenant-" + role;
    jdbc.sql(
            "INSERT INTO role(id,tenant_id,name,created_at) VALUES(:id,'tenant-one',:name,CURRENT_TIMESTAMP)")
        .param("id", role)
        .param("name", name)
        .update();
    jdbc.sql("INSERT INTO role_permission(role_id,permission) VALUES(:id,'course:manage')")
        .param("id", role)
        .update();
    jdbc.sql(
            "INSERT INTO role_grant(subject_type,subject_id,role_id,scope_type,scope_id,granted_at,granted_by) VALUES('USER',:subject,:role,'*','*',CURRENT_TIMESTAMP,'fixture')")
        .param("subject", subject)
        .param("role", role)
        .update();
    return name;
  }
}
