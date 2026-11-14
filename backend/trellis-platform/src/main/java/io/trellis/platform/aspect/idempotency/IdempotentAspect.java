package io.trellis.platform.aspect.idempotency;

import io.trellis.common.annotation.Idempotent;
import io.trellis.common.error.ConflictException;
import io.trellis.common.error.PlatformErrorCode;
import io.trellis.platform.aspect.AspectOrder;
import io.trellis.platform.aspect.support.AspectContexts;
import io.trellis.platform.aspect.support.SpelKeyEvaluator;
import java.lang.reflect.Method;
import java.time.Duration;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/** Enforces {@link Idempotent}; ordered after authorization and before lock/transaction (see AspectOrder). */
@Aspect
@Order(AspectOrder.IDEMPOTENT)
public class IdempotentAspect {
    private final IdempotencyStore store;
    private final SpelKeyEvaluator spel;
    private final JsonMapper json;
    private final IdempotencyProperties properties;

    public IdempotentAspect(
            IdempotencyStore store, SpelKeyEvaluator spel, JsonMapper json, IdempotencyProperties properties) {
        this.store = store;
        this.spel = spel;
        this.json = json;
        this.properties = properties;
    }

    @Around("@annotation(idempotent)")
    public Object idempotent(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        Method method = AspectContexts.method(pjp);
        String key = spel.evaluate(idempotent.key(), method, pjp.getArgs());
        if (key == null || key.isBlank()) {
            return pjp.proceed();
        }
        String scope = AspectContexts.operationName(pjp);
        IdempotencyStore.Claim claim = store.claim(scope, key, properties.inProgressTimeout());
        return switch (claim) {
            case IdempotencyStore.Claim.Replay replay -> deserialize(replay.resultJson(), method);
            case IdempotencyStore.Claim.InProgress inProgress ->
                throw new ConflictException(
                        PlatformErrorCode.IDEMPOTENCY_IN_PROGRESS,
                        "A request with this idempotency key is still running");
            case IdempotencyStore.Claim.Acquired acquired ->
                runAndStore(pjp, scope, key, Duration.parse(idempotent.ttl()));
        };
    }

    private Object runAndStore(ProceedingJoinPoint pjp, String scope, String key, Duration ttl) throws Throwable {
        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable e) {
            store.release(scope, key);
            throw e;
        }
        // The business transaction has committed by now. If storing fails the claim expires after the
        // in-progress timeout; until then retries get 409, never a second execution.
        store.complete(scope, key, json.writeValueAsString(result), ttl);
        return result;
    }

    private Object deserialize(String stored, Method method) {
        if (method.getReturnType() == void.class) {
            return null;
        }
        JavaType type = json.getTypeFactory().constructType(method.getGenericReturnType());
        return json.readValue(stored, type);
    }
}
