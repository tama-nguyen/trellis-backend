package io.trellis.platform.aspect.idempotency;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Postgres implementation. Claims use {@code INSERT ... ON CONFLICT DO UPDATE ... WHERE expired},
 * so taking a fresh key and taking over an expired one are the same atomic statement. Every
 * operation runs in its own REQUIRES_NEW transaction: the claim must be visible to concurrent
 * retries before the business work starts.
 */
public class JdbcIdempotencyStore implements IdempotencyStore {
  static final String IN_PROGRESS = "IN_PROGRESS";
  static final String COMPLETED = "COMPLETED";

  private static final String CLAIM =
      """
            INSERT INTO idempotency_record (scope, idem_key, status, created_at, updated_at, expires_at)
            VALUES (:scope, :key, 'IN_PROGRESS', :now, :now, :expiresAt)
            ON CONFLICT (scope, idem_key) DO UPDATE
               SET status = 'IN_PROGRESS', response = NULL, created_at = :now, updated_at = :now,
                   expires_at = :expiresAt
             WHERE idempotency_record.expires_at <= :now
            RETURNING idem_key
            """;
  private static final String SELECT =
      """
            SELECT status, response FROM idempotency_record WHERE scope = :scope AND idem_key = :key
            """;
  private static final String COMPLETE =
      """
            UPDATE idempotency_record SET status = 'COMPLETED', response = :response, updated_at = :now,
                   expires_at = :expiresAt
             WHERE scope = :scope AND idem_key = :key
            """;
  private static final String RELEASE =
      "DELETE FROM idempotency_record WHERE scope = :scope AND idem_key = :key AND status = 'IN_PROGRESS'";
  private static final String PURGE = "DELETE FROM idempotency_record WHERE expires_at <= :now";

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final Clock clock;

  public JdbcIdempotencyStore(
      final JdbcClient jdbc,
      final PlatformTransactionManager transactionManager,
      final Clock clock) {
    this.jdbc = jdbc;
    this.tx = new TransactionTemplate(transactionManager);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.clock = clock;
  }

  @Override
  public Claim claim(final String scope, final String key, final Duration inProgressTimeout) {
    final Instant now = clock.instant();
    return tx.execute(
        status -> {
          final boolean acquired =
              jdbc.sql(CLAIM)
                  .param("scope", scope)
                  .param("key", key)
                  .param("now", Timestamp.from(now))
                  .param("expiresAt", Timestamp.from(now.plus(inProgressTimeout)))
                  .query(String.class)
                  .optional()
                  .isPresent();
          if (acquired) {
            return new Claim.Acquired();
          }
          return jdbc.sql(SELECT)
              .param("scope", scope)
              .param("key", key)
              .query(
                  (rs, i) ->
                      COMPLETED.equals(rs.getString("status"))
                          ? (Claim) new Claim.Replay(rs.getString("response"))
                          : new Claim.InProgress())
              .optional()
              // Row vanished between statements (released by the other call): let the client retry.
              .orElse(new Claim.InProgress());
        });
  }

  @Override
  public void complete(
      final String scope, final String key, final String resultJson, final Duration ttl) {
    final Instant now = clock.instant();
    tx.executeWithoutResult(
        status ->
            jdbc.sql(COMPLETE)
                .param("scope", scope)
                .param("key", key)
                .param("response", resultJson)
                .param("now", Timestamp.from(now))
                .param("expiresAt", Timestamp.from(now.plus(ttl)))
                .update());
  }

  @Override
  public void release(final String scope, final String key) {
    tx.executeWithoutResult(
        status -> jdbc.sql(RELEASE).param("scope", scope).param("key", key).update());
  }

  @Scheduled(cron = "${trellis.idempotency.cleanup-cron:0 17 * * * *}")
  public void purgeExpired() {
    tx.executeWithoutResult(
        status -> jdbc.sql(PURGE).param("now", Timestamp.from(clock.instant())).update());
  }
}
