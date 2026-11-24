package io.trellis.app;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.trellis.common.aop.RateLimited;
import io.trellis.common.error.RateLimitedException;
import io.trellis.platform.aspect.ratelimit.RateLimitEngine;
import io.trellis.platform.aspect.ratelimit.RateLimitedAspect;
import io.trellis.platform.aspect.support.SpelKeyEvaluator;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import lombok.val;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

class RateLimitedAspectTest {
  private final RateLimitEngine engine = mock(RateLimitEngine.class);
  private final SpelKeyEvaluator spel = mock(SpelKeyEvaluator.class);
  private final RateLimitedAspect aspect = new RateLimitedAspect(engine, spel, Clock.systemUTC());

  @Test
  void skipVoidSuppressesInvocation() throws Throwable {
    // Given
    val method = Operations.class.getDeclaredMethod("recovery", String.class);
    val call = call(method);
    when(spel.evaluate(anyString(), eq(method), any())).thenReturn("user@example.test");
    when(engine.consume("recovery", "user@example.test")).thenReturn(Duration.ofMinutes(1));

    // When
    val response = aspect.limit(call, method.getAnnotation(RateLimited.class));

    // Then
    assertNull(response);
    verify(call, never()).proceed();
  }

  @Test
  void skipVoidRejectsReturnValueBeforeConsumption() throws Throwable {
    // Given
    val method = Operations.class.getDeclaredMethod("invalid", String.class);
    val call = call(method);

    // When
    assertThrows(
        IllegalStateException.class,
        () -> aspect.limit(call, method.getAnnotation(RateLimited.class)));

    // Then
    verifyNoInteractions(engine);
    verify(call, never()).proceed();
  }

  @Test
  void defaultPolicyRejectsExhaustedQuota() throws Throwable {
    // Given
    val method = Operations.class.getDeclaredMethod("login", String.class);
    val call = call(method);
    when(spel.evaluate(anyString(), eq(method), any())).thenReturn("user@example.test");
    when(engine.consume("login", "user@example.test")).thenReturn(Duration.ofMinutes(1));

    // When
    assertThrows(
        RateLimitedException.class,
        () -> aspect.limit(call, method.getAnnotation(RateLimited.class)));

    // Then
    verify(call, never()).proceed();
  }

  private ProceedingJoinPoint call(final Method method) {
    val call = mock(ProceedingJoinPoint.class);
    val signature = mock(MethodSignature.class);
    when(call.getSignature()).thenReturn(signature);
    when(signature.getMethod()).thenReturn(method);
    return call;
  }

  static class Operations {
    @RateLimited(policy = "recovery", key = "#email", onExceeded = RateLimited.OnExceeded.SKIP_VOID)
    public void recovery(final String email) {}

    @RateLimited(policy = "invalid", key = "#email", onExceeded = RateLimited.OnExceeded.SKIP_VOID)
    public String invalid(final String email) {
      return email;
    }

    @RateLimited(policy = "login", key = "#email")
    public void login(final String email) {}
  }
}
