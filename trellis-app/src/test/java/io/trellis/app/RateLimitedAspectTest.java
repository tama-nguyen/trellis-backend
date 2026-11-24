package io.trellis.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.trellis.common.aop.RateLimited;
import io.trellis.common.error.RateLimitedException;
import io.trellis.platform.aspect.ratelimit.*;
import io.trellis.platform.aspect.support.SpelKeyEvaluator;
import java.lang.reflect.Method;
import java.time.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

class RateLimitedAspectTest {
  private final RateLimitEngine engine = mock(RateLimitEngine.class);
  private final SpelKeyEvaluator spel = mock(SpelKeyEvaluator.class);
  private final RateLimitedAspect aspect = new RateLimitedAspect(engine, spel, Clock.systemUTC());

  @Test
  void skipVoidSuppressesInvocation() throws Throwable {
    var method = Operations.class.getDeclaredMethod("recovery", String.class);
    var call = call(method);
    when(spel.evaluate(anyString(), eq(method), any())).thenReturn("user@example.test");
    when(engine.consume("recovery", "user@example.test")).thenReturn(Duration.ofMinutes(1));
    assertNull(aspect.limit(call, method.getAnnotation(RateLimited.class)));
    verify(call, never()).proceed();
  }

  @Test
  void skipVoidRejectsReturnValueBeforeConsumption() throws Throwable {
    var method = Operations.class.getDeclaredMethod("invalid", String.class);
    var call = call(method);
    assertThrows(
        IllegalStateException.class,
        () -> aspect.limit(call, method.getAnnotation(RateLimited.class)));
    verifyNoInteractions(engine);
    verify(call, never()).proceed();
  }

  @Test
  void defaultPolicyRejectsExhaustedQuota() throws Throwable {
    var method = Operations.class.getDeclaredMethod("login", String.class);
    var call = call(method);
    when(spel.evaluate(anyString(), eq(method), any())).thenReturn("user@example.test");
    when(engine.consume("login", "user@example.test")).thenReturn(Duration.ofMinutes(1));
    assertThrows(
        RateLimitedException.class,
        () -> aspect.limit(call, method.getAnnotation(RateLimited.class)));
    verify(call, never()).proceed();
  }

  private ProceedingJoinPoint call(Method method) {
    var call = mock(ProceedingJoinPoint.class);
    var signature = mock(MethodSignature.class);
    when(call.getSignature()).thenReturn(signature);
    when(signature.getMethod()).thenReturn(method);
    return call;
  }

  static class Operations {
    @RateLimited(policy = "recovery", key = "#email", onExceeded = RateLimited.OnExceeded.SKIP_VOID)
    public void recovery(String email) {}

    @RateLimited(policy = "invalid", key = "#email", onExceeded = RateLimited.OnExceeded.SKIP_VOID)
    public String invalid(String email) {
      return email;
    }

    @RateLimited(policy = "login", key = "#email")
    public void login(String email) {}
  }
}
