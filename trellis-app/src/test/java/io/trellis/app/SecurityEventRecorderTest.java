package io.trellis.app;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.SecurityAction;
import io.trellis.platform.security.audit.SecurityEventRecorder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class SecurityEventRecorderTest {
  @Test
  void sinkFailureUsesBoundedFallbackWithoutReplacingBusinessOutcome() {
    // Given
    val jdbc = mock(JdbcClient.class);
    when(jdbc.sql(anyString())).thenThrow(new IllegalStateException("database unavailable"));
    val manager = mock(PlatformTransactionManager.class);
    when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    val meters = new SimpleMeterRegistry();
    val recorder =
        new SecurityEventRecorder(
            jdbc, manager, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), meters);
    val ctx =
        RequestContext.builder().correlationId("audit-test").requestTime(Instant.EPOCH).build();

    // When
    assertDoesNotThrow(
        () ->
            recorder.record(
                ctx,
                SecurityAction.LOGIN,
                SecurityEventRecorder.Outcome.REJECTED,
                "INVALID_CREDENTIALS"));

    // Then
    assertEquals(
        1, meters.get("trellis.security.audit").tag("outcome", "unavailable").counter().count());
    verify(manager).rollback(any());
  }
}
