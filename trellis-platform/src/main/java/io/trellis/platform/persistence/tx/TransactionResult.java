package io.trellis.platform.persistence.tx;

/**
 * Outcome of a unit of work run by {@link RetryingTransactions}. Rollback is the default: work that wants its
 * changes kept must say {@link #commit(Object)}, so an early {@code return} on a guard never commits half a
 * change by accident.
 */
public record TransactionResult<T>(T value, boolean commit) {
    public static <T> TransactionResult<T> commit(T value) {
        return new TransactionResult<>(value, true);
    }

    public static <T> TransactionResult<T> rollback(T value) {
        return new TransactionResult<>(value, false);
    }
}
