package io.trellis.platform.aspect.idempotency;

import java.time.Duration;

/** Storage of idempotency claims and results. Each call commits on its own, outside the business transaction. */
public interface IdempotencyStore {

    Claim claim(String scope, String key, Duration inProgressTimeout);

    void complete(String scope, String key, String resultJson, Duration ttl);

    void release(String scope, String key);

    /** Outcome of {@link #claim}. */
    sealed interface Claim {
        /** First call: run the method. */
        record Acquired() implements Claim {}

        /** Already completed: return the stored result. */
        record Replay(String resultJson) implements Claim {}

        /** Another call with this key is running. */
        record InProgress() implements Claim {}
    }
}
