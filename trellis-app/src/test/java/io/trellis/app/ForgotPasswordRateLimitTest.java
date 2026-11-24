package io.trellis.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.trellis.common.error.ServiceUnavailableException;
import io.trellis.identity.api.security.IdentityRateLimitPolicies;
import io.trellis.identity.application.port.UserMailSender;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.support.PasswordResetCodes;
import io.trellis.platform.aspect.ratelimit.RateLimitEngine;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(PostgresTestcontainer.class)
class ForgotPasswordRateLimitTest {
  @LocalServerPort private int port;
  @MockitoBean private RateLimitEngine engine;
  @MockitoBean private UserRepository users;
  @MockitoBean private UserMailSender mail;
  @MockitoBean private PasswordResetCodes codes;

  @BeforeEach
  void allowIngress() {
    when(engine.consume(anyString(), anyString())).thenReturn(Duration.ZERO);
  }

  @Test
  void suppressionDoesNotReachPersistenceOrMailer() throws Exception {
    when(engine.consume(eq(IdentityRateLimitPolicies.FORGOT), anyString()))
        .thenAnswer(
            call -> {
              assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
              return Duration.ofMinutes(1);
            });
    assertEquals(202, request());
    verify(engine).consume(IdentityRateLimitPolicies.FORGOT, "USER@Example.Test");
    verifyNoInteractions(users, mail, codes);
  }

  @Test
  void allowedRequestOpensTransactionAfterControllerQuota() throws Exception {
    when(engine.consume(eq(IdentityRateLimitPolicies.FORGOT), anyString()))
        .thenAnswer(
            call -> {
              assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
              return Duration.ZERO;
            });
    when(users.userByEmail(any()))
        .thenAnswer(
            call -> {
              assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
              return Optional.empty();
            });
    assertEquals(202, request());
    verify(users).userByEmail(any());
    verifyNoInteractions(mail, codes);
  }

  @Test
  void backendFailureReturns503WithoutCallingUseCase() throws Exception {
    when(engine.consume(eq(IdentityRateLimitPolicies.FORGOT), anyString()))
        .thenThrow(new ServiceUnavailableException("offline", null, null));
    assertEquals(503, request());
    verifyNoInteractions(users, mail, codes);
  }

  private int request() throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/forgot-password"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"USER@Example.Test\"}"))
            .build();
    try (var client = HttpClient.newHttpClient()) {
      return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
  }
}
