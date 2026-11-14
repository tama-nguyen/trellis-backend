package io.trellis.platform.aspect.lock;

import io.micrometer.core.instrument.MeterRegistry;
import io.trellis.platform.aspect.AspectOrder;
import io.trellis.platform.aspect.support.AspectContexts;
import io.trellis.platform.aspect.support.SpelKeyEvaluator;
import io.trellis.common.error.ConflictException;
import io.trellis.common.error.PlatformErrorCode;
import java.time.Duration;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

/** Enforces {@link DistributedLock}; ordered outside the transaction (see AspectOrder). */
@Aspect
@Order(AspectOrder.DISTRIBUTED_LOCK)
public class DistributedLockAspect {
    private static final Logger log = LoggerFactory.getLogger(DistributedLockAspect.class);
    private static final String LEASE_OVERRUN_METRIC = "trellis.lock.lease.overrun";

    private final LockProvider locks;
    private final SpelKeyEvaluator spel;
    private final MeterRegistry meters;

    public DistributedLockAspect(LockProvider locks, SpelKeyEvaluator spel, MeterRegistry meters) {
        this.locks = locks;
        this.spel = spel;
        this.meters = meters;
    }

    @Around("@annotation(lock)")
    public Object lock(ProceedingJoinPoint pjp, DistributedLock lock) throws Throwable {
        String operation = AspectContexts.operationName(pjp);
        String name = operation + ":" + spel.evaluate(lock.key(), AspectContexts.method(pjp), pjp.getArgs());
        Optional<LockProvider.Handle> handle = locks.tryLock(name, Duration.parse(lock.waitFor()));
        if (handle.isEmpty()) {
            throw new ConflictException(PlatformErrorCode.LOCK_NOT_ACQUIRED, "The resource is busy; retry shortly");
        }
        long start = System.nanoTime();
        try (LockProvider.Handle held = handle.get()) {
            return pjp.proceed();
        } finally {
            Duration held = Duration.ofNanos(System.nanoTime() - start);
            if (held.compareTo(Duration.parse(lock.leaseFor())) > 0) {
                meters.counter(LEASE_OVERRUN_METRIC, "operation", operation).increment();
                log.atWarn()
                        .addKeyValue("operation", operation)
                        .addKeyValue("held_ms", held.toMillis())
                        .log("Distributed lock held longer than its lease budget");
            }
        }
    }
}
