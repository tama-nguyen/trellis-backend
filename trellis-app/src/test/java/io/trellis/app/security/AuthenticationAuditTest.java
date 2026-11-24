package io.trellis.app.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.trellis.app.support.AuditProbe;
import io.trellis.app.support.IdentityApi;
import io.trellis.app.support.SecurityTestContext;
import io.trellis.common.security.SecurityAction;
import io.trellis.platform.security.audit.SecurityEventRecorder.Outcome;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

@SecurityTestContext
@RequiredArgsConstructor
class AuthenticationAuditTest {
  @LocalServerPort private int port;

  private final IdentityApi api;

  private final AuditProbe audit;

  @Test
  void failedLoginRecordsRejectedOutcomeWithoutSecretFields() throws Exception {
    // Given
    val secret = "Never-record-this-password";

    // When
    val response = api.login(port, UUID.randomUUID() + "@example.test", secret);

    // Then
    assertEquals(HttpStatus.UNAUTHORIZED.value(), response.statusCode());
    val event =
        audit.securityEvent(response.headers().firstValue("X-Correlation-Id").orElseThrow());
    assertEquals(SecurityAction.LOGIN.name(), event.get("action"));
    assertEquals(Outcome.REJECTED.name(), event.get("outcome"));
    assertTrue(
        event.values().stream().map(String::valueOf).noneMatch(value -> value.contains(secret)));
  }
}
