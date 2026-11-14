package io.trellis.common.error;

/**
 * Optimistic-lock conflict: the row's version changed between read and write. Retried automatically by
 * {@code RetryingTransactions}; surfaces as 409 when retries run out.
 */
public class ConcurrentModificationException extends TrellisException {
    public ConcurrentModificationException(String aggregateType, String aggregateId) {
        super(
                PlatformErrorCode.CONCURRENT_MODIFICATION,
                aggregateType + " " + aggregateId + " was modified concurrently");
    }
}
