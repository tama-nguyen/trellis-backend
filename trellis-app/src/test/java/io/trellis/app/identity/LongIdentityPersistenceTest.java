package io.trellis.app.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.trellis.app.support.IdentityFixtures;
import io.trellis.app.support.SecurityTestContext;
import io.trellis.identity.domain.user.UserStatus;
import io.trellis.identity.infrastructure.persistence.user.UserPasswordJpaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SecurityTestContext
@Import(LongIdentityPersistenceTest.SqlInspection.class)
@RequiredArgsConstructor
class LongIdentityPersistenceTest {
  private static final ThreadLocal<List<String>> CAPTURED_SQL = new ThreadLocal<>();
  private final IdentityFixtures fixtures;
  private final UserPasswordJpaRepository passwords;

  @Test
  void shouldInsertGeneratedUserAndSharedKeyPasswordWithoutMergeLookups() {
    // Given
    val statements = new ArrayList<String>();

    // When
    val userId = createUserAndCaptureSql(statements);

    // Then
    assertTrue(userId > 0);
    assertTrue(statements.stream().anyMatch(sql -> sql.startsWith("insert into users ")));
    assertTrue(statements.stream().anyMatch(sql -> sql.startsWith("insert into user_password ")));
    assertFalse(
        statements.stream()
            .anyMatch(
                sql ->
                    sql.startsWith("select ")
                        && (sql.contains(" from users ") || sql.contains(" from user_password "))));
    val password = passwords.findById(userId).orElseThrow();
    assertEquals(userId, password.getUserId());
    assertFalse(password.isNew());
  }

  private Long createUserAndCaptureSql(final List<String> statements) {
    CAPTURED_SQL.set(statements);
    try {
      return fixtures.nativeUser(UserStatus.ACTIVE);
    } finally {
      CAPTURED_SQL.remove();
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class SqlInspection {
    @Bean
    HibernatePropertiesCustomizer captureEntityCreationSql() {
      return properties ->
          properties.put(
              "hibernate.session_factory.statement_inspector",
              (StatementInspector)
                  sql -> {
                    val captured = CAPTURED_SQL.get();
                    if (captured != null) {
                      captured.add(sql.toLowerCase(Locale.ROOT));
                    }
                    return sql;
                  });
    }
  }
}
