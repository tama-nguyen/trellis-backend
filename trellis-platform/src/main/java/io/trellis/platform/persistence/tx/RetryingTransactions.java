package io.trellis.platform.persistence.tx;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.trellis.common.error.ConcurrentModificationException;
import io.trellis.common.retry.Backoff;
import java.util.function.Function;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a unit of work in its own transaction and retries it, with backoff, when it loses an
 * optimistic-lock race or hits a deadlock/serialization failure. Use it for read-modify-write paths
 * where two users can edit the same row; plain {@code @Transactional} is fine everywhere else.
 *
 * <p>Must be called outside a transaction: retrying inside an outer transaction would re-run on the
 * same snapshot and fail the same way. Metric {@code trellis.tx.retries{operation,outcome}} counts
 * retries and exhausted attempts so hot rows show up on a dashboard.
 */
public final class RetryingTransactions {
  static final String METRIC = "trellis.tx.retries";
  private static final String TAG_OPERATION = "operation";
  private static final String TAG_OUTCOME = "outcome";

  private final TransactionTemplate template;
  private final MeterRegistry meters;
  private final TransactionRetryProperties properties;
  private final Backoff backoff;

  public RetryingTransactions(
      PlatformTransactionManager transactionManager,
      MeterRegistry meters,
      TransactionRetryProperties properties) {
    this.template = new TransactionTemplate(transactionManager);
    this.meters = meters;
    this.properties = properties;
    this.backoff = new Backoff(properties.baseBackoff(), properties.maxBackoff());
  }

  /**
   * @param operation low-cardinality name for metrics, e.g. {@code notice.update}
   * @param work runs inside the transaction; return {@link TransactionResult#commit} to keep its
   *     changes
   */
  public <T> T execute(String operation, Function<TransactionStatus, TransactionResult<T>> work) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "RetryingTransactions must not run inside an existing transaction");
    }
    for (int attempt = 0; ; attempt++) {
      try {
        return template.execute(
            status -> {
              TransactionResult<T> result = work.apply(status);
              if (!result.commit()) {
                status.setRollbackOnly();
              }
              return result.value();
            });
      } catch (ConcurrentModificationException | ConcurrencyFailureException e) {
        if (attempt + 1 >= properties.maxAttempts()) {
          counter(operation, "exhausted").increment();
          throw e;
        }
        counter(operation, "retried").increment();
        sleep(attempt);
      }
    }
  }

  private Counter counter(String operation, String outcome) {
    return meters.counter(METRIC, TAG_OPERATION, operation, TAG_OUTCOME, outcome);
  }

  private void sleep(int attempt) {
    try {
      Thread.sleep(backoff.delay(attempt));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while backing off a transaction retry", e);
    }
  }
}
