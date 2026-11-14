package io.trellis.platform.aspect.lock;

import java.time.Duration;
import java.util.Optional;

/** Cluster-wide named locks. */
public interface LockProvider {
    /** Tries to take the lock within {@code waitFor}; the handle releases it on close. */
    Optional<Handle> tryLock(String name, Duration waitFor);

    /** A held lock. Closing releases it; closing twice is harmless. */
    interface Handle extends AutoCloseable {
        @Override
        void close();
    }
}
