package io.trellis.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class ActionTokenMigrationTest {
  private static final String POSTGRES_IMAGE = "postgres:17-alpine";
  private static final String LEGACY_SCHEMA = "db/changelog/identity/001-native-identity.sql";
  private static final String ACTION_SERIALIZATION =
      "db/changelog/identity/003-action-token-serialization.sql";
  private static final String UNIQUE_VIOLATION = "23505";
  private static final String INSERT_HISTORICAL_ACTION =
      """
      INSERT INTO user_action_token(id, user_id, token_type, token_hash, expires_at, created_at)
      VALUES (?, ?, 'VERIFY_EMAIL', ?, CURRENT_TIMESTAMP + INTERVAL '1 hour', ?)
      """;
  private static final String INSERT_DUPLICATE_ACTION =
      """
      INSERT INTO user_action_token(id, user_id, token_type, token_hash, expires_at, created_at)
      VALUES (?, ?, 'VERIFY_EMAIL', 'duplicate-digest',
              CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP)
      """;
  private static final String UNCONSUMED_ACTION_IDS =
      "SELECT id FROM user_action_token WHERE consumed_at IS NULL";

  @Container
  private final PostgreSQLContainer postgresqlContainer = new PostgreSQLContainer(POSTGRES_IMAGE);

  private Connection connection;

  @BeforeEach
  void createLegacyDatabase() throws Exception {
    connection =
        DriverManager.getConnection(
            postgresqlContainer.getJdbcUrl(),
            postgresqlContainer.getUsername(),
            postgresqlContainer.getPassword());
    executeMigration(LEGACY_SCHEMA);
  }

  @AfterEach
  void closeDatabaseConnection() throws SQLException {
    if (connection != null) {
      connection.close();
    }
  }

  @Test
  void shouldRetireOlderActionsWhenMigratingHistoricalDuplicates() throws Exception {
    // Given
    val historicalActions = createHistoricalDuplicates();

    // When
    executeMigration(ACTION_SERIALIZATION);

    // Then
    val unconsumedActions = unconsumedActionIds();
    assertEquals(List.of(historicalActions.latestActionId()), unconsumedActions);
  }

  @Test
  void shouldRejectAnotherOutstandingActionAfterMigration() throws Exception {
    // Given
    val historicalActions = createHistoricalDuplicates();
    executeMigration(ACTION_SERIALIZATION);

    // When
    val failure =
        assertThrows(
            SQLException.class, () -> insertAnotherOutstandingAction(historicalActions.userId()));

    // Then
    assertEquals(UNIQUE_VIOLATION, failure.getSQLState());
    val unconsumedActions = unconsumedActionIds();
    assertEquals(List.of(historicalActions.latestActionId()), unconsumedActions);
  }

  private HistoricalActions createHistoricalDuplicates() throws SQLException {
    val userId = 1L;
    val olderActionId = 1L;
    val latestActionId = 2L;

    insertHistoricalAction(
        olderActionId, userId, "old-digest", Timestamp.valueOf("2026-01-01 00:00:00"));
    insertHistoricalAction(
        latestActionId, userId, "latest-digest", Timestamp.valueOf("2026-01-02 00:00:00"));

    return new HistoricalActions(userId, latestActionId);
  }

  private void insertHistoricalAction(
      final Long actionId, final Long userId, final String tokenHash, final Timestamp createdAt)
      throws SQLException {
    try (val insert = connection.prepareStatement(INSERT_HISTORICAL_ACTION)) {
      insert.setObject(1, actionId);
      insert.setObject(2, userId);
      insert.setString(3, tokenHash);
      insert.setTimestamp(4, createdAt);
      insert.executeUpdate();
    }
  }

  private void insertAnotherOutstandingAction(final Long userId) throws SQLException {
    try (val insert = connection.prepareStatement(INSERT_DUPLICATE_ACTION)) {
      insert.setObject(1, 3L);
      insert.setObject(2, userId);
      insert.executeUpdate();
    }
  }

  private List<Long> unconsumedActionIds() throws SQLException {
    val unconsumedActions = new ArrayList<Long>();
    try (val statement = connection.createStatement();
        val rows = statement.executeQuery(UNCONSUMED_ACTION_IDS)) {
      while (rows.next()) {
        unconsumedActions.add(rows.getObject(1, Long.class));
      }
    }
    return unconsumedActions;
  }

  private void executeMigration(final String resourcePath) throws Exception {
    val migrationSql = readMigration(resourcePath);
    try (val statement = connection.createStatement()) {
      statement.execute(migrationSql);
    }
  }

  private String readMigration(final String resourcePath) throws Exception {
    val migration = new ClassPathResource(resourcePath);
    try (val input = migration.getInputStream()) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private record HistoricalActions(Long userId, Long latestActionId) {}
}
