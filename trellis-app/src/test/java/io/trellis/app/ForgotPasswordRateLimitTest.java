package io.trellis.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.trellis.app.support.IdentityApi;
import io.trellis.app.support.TestApiClient;
import io.trellis.common.error.ServiceUnavailableException;
import io.trellis.identity.api.security.IdentityRateLimitPolicies;
import io.trellis.identity.application.port.UserMailSender;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.support.PasswordResetCodes;
import io.trellis.platform.aspect.ratelimit.RateLimitEngine;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({PostgresTestcontainer.class, IdentityApi.class, TestApiClient.class})
@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ForgotPasswordRateLimitTest {
  private static final String SUBMITTED_EMAIL = "USER@Example.Test";
  @LocalServerPort private int port;
  @MockitoBean private RateLimitEngine engine;
  @MockitoBean private UserRepository users;
  @MockitoBean private UserMailSender mail;
  @MockitoBean private PasswordResetCodes codes;
  private final IdentityApi api;

  @BeforeEach
  void allowIngress() {
    when(engine.consume(anyString(), anyString())).thenReturn(Duration.ZERO);
  }

  @Test
  void suppressionDoesNotReachPersistenceOrMailer() throws Exception {
    // Given
    val quotaTransaction = new AtomicReference<Boolean>();
    when(engine.consume(eq(IdentityRateLimitPolicies.FORGOT), anyString()))
        .thenAnswer(
            call -> {
              quotaTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
              return Duration.ofMinutes(1);
            });

    // When
    val response = api.forgotPassword(port, SUBMITTED_EMAIL);

    // Then
    assertEquals(HttpStatus.ACCEPTED.value(), response.statusCode());
    assertEquals(Boolean.FALSE, quotaTransaction.get());
    verify(engine).consume(IdentityRateLimitPolicies.FORGOT, SUBMITTED_EMAIL);
    verifyNoInteractions(users, mail, codes);
  }

  @Test
  void allowedRequestOpensTransactionAfterControllerQuota() throws Exception {
    // Given
    val quotaTransaction = new AtomicReference<Boolean>();
    val lookupTransaction = new AtomicReference<Boolean>();
    when(engine.consume(eq(IdentityRateLimitPolicies.FORGOT), anyString()))
        .thenAnswer(
            call -> {
              quotaTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
              return Duration.ZERO;
            });
    when(users.userByEmail(any()))
        .thenAnswer(
            call -> {
              lookupTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
              return Optional.empty();
            });

    // When
    val response = api.forgotPassword(port, SUBMITTED_EMAIL);

    // Then
    assertEquals(HttpStatus.ACCEPTED.value(), response.statusCode());
    assertEquals(Boolean.FALSE, quotaTransaction.get());
    assertEquals(Boolean.TRUE, lookupTransaction.get());
    verify(users).userByEmail(any());
    verifyNoInteractions(mail, codes);
  }

  @Test
  void backendFailureReturns503WithoutCallingUseCase() throws Exception {
    // Given
    when(engine.consume(eq(IdentityRateLimitPolicies.FORGOT), anyString()))
        .thenThrow(new ServiceUnavailableException("offline", null, null));

    // When
    val response = api.forgotPassword(port, SUBMITTED_EMAIL);

    // Then
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.statusCode());
    verifyNoInteractions(users, mail, codes);
  }
}
