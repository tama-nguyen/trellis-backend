package io.trellis.platform.aspect.audit;

import io.trellis.common.annotation.Audited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.id.UuidV7;
import io.trellis.platform.aspect.AspectOrder;
import io.trellis.platform.aspect.support.AspectContexts;
import io.trellis.platform.aspect.support.SpelKeyEvaluator;
import java.time.Clock;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Enforces {@link Audited}; ordered inside the transaction (see {@code AspectOrder}). */
@Aspect
@Order(AspectOrder.AUDITED)
public class AuditedAspect {
    private final AuditRecorder recorder;
    private final SpelKeyEvaluator spel;
    private final Clock clock;

    public AuditedAspect(AuditRecorder recorder, SpelKeyEvaluator spel, Clock clock) {
        this.recorder = recorder;
        this.spel = spel;
        this.clock = clock;
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("@Audited requires an active transaction: add @Transactional to "
                    + AspectContexts.operationName(pjp));
        }
        Object result = pjp.proceed();
        RequestContext ctx = AspectContexts.requestContext(pjp, clock);
        String aggregateId = audited.aggregateId().isEmpty()
                ? null
                : spel.evaluate(
                        audited.aggregateId(),
                        AspectContexts.method(pjp),
                        pjp.getArgs(),
                        result == null ? Map.of() : Map.of(SpelKeyEvaluator.RESULT_VARIABLE, result));
        recorder.record(new AuditEntry(
                UuidV7.next(clock), clock.instant(), ctx, audited.action(), audited.aggregateType(), aggregateId));
        return result;
    }
}
