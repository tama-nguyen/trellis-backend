package io.trellis.app.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.trellis.app.support.IdentityRegistrationFixtures;
import io.trellis.app.support.IdentityRegistrationFixtures.PendingAccount;
import io.trellis.app.support.MailWorkerFixture;
import io.trellis.app.support.SecurityTestContext;
import io.trellis.identity.IdentityMailProperties;
import io.trellis.identity.application.port.IdentityMailDelivery;
import io.trellis.identity.infrastructure.persistence.action.UserActionTokenEntity;
import io.trellis.identity.infrastructure.persistence.action.UserActionTokenJpaRepository;
import io.trellis.identity.infrastructure.persistence.mail.IdentityMailJobRepository;
import io.trellis.identity.infrastructure.persistence.mail.IdentityMailJobStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Example;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SecurityTestContext
@RequiredArgsConstructor
class IdentityMailDeliveryTest {
  private static final Duration CONCURRENCY_TIMEOUT = Duration.ofSeconds(10);
  private final IdentityRegistrationFixtures fixtures;
  private final IdentityMailJobRepository mailJobs;
  private final UserActionTokenJpaRepository actions;
  private final IdentityMailDelivery delivery;
  private final MailWorkerFixture workers;
  private final IdentityMailProperties properties;
  private PendingAccount account;

  @BeforeEach
  void createPendingAccount() {
    account = fixtures.pendingAccount();
  }

  @AfterEach
  void removeOwnedMailJob() {
    mailJobs.deleteById(account.mailJobId());
  }

  @Test
  void deliveryRunsOutsideTransactionAndClearsPayload() {
    // Given
    val transactionActive = new AtomicBoolean(true);
    doAnswer(
            call -> {
              transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
              return null;
            })
        .when(delivery)
        .sendEmailVerification(anyString(), anyString(), anyString());

    // When
    workers.worker().tick();

    // Then
    verify(delivery)
        .sendEmailVerification(eq(account.registration().email()), anyString(), anyString());
    assertFalse(transactionActive.get());
    val job = mailJobs.findById(account.mailJobId()).orElseThrow();
    assertEquals(IdentityMailJobStatus.SENT, job.getStatus());
    assertNotNull(job.getCompletedAt());
    assertEquals("", job.getPayload());
  }

  @Test
  void retryReusesOriginalSecret() {
    // Given
    failDelivery();
    workers.worker().tick();
    val firstAttempt = mailJobs.findById(account.mailJobId()).orElseThrow();

    // When
    workers.at(firstAttempt.getNextAttemptAt()).tick();

    // Then
    val secrets = ArgumentCaptor.forClass(String.class);
    verify(delivery, times(2))
        .sendEmailVerification(eq(account.registration().email()), anyString(), secrets.capture());
    assertEquals(secrets.getAllValues().getFirst(), secrets.getAllValues().getLast());
    assertEquals(
        IdentityMailJobStatus.PENDING,
        mailJobs.findById(account.mailJobId()).orElseThrow().getStatus());
  }

  @Test
  void failedAttemptWaitsUntilScheduledRetry() {
    // Given
    failDelivery();
    workers.worker().tick();
    val firstAttempt = mailJobs.findById(account.mailJobId()).orElseThrow();
    val beforeRetry = firstAttempt.getNextAttemptAt().minusMillis(1);

    // When
    workers.at(beforeRetry).tick();

    // Then
    verify(delivery)
        .sendEmailVerification(eq(account.registration().email()), anyString(), anyString());
    val unchanged = mailJobs.findById(account.mailJobId()).orElseThrow();
    assertEquals(firstAttempt.getAttempts(), unchanged.getAttempts());
    assertEquals(IdentityMailJobStatus.PENDING, unchanged.getStatus());
  }

  @Test
  void activeLeasePreventsConcurrentDelivery() throws Exception {
    // Given
    val sending = new CountDownLatch(1);
    val release = new CountDownLatch(1);
    val now = Instant.now();
    doAnswer(
            call -> {
              sending.countDown();
              assertTrue(release.await(CONCURRENCY_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
              return null;
            })
        .when(delivery)
        .sendEmailVerification(anyString(), anyString(), anyString());

    try (val executor = Executors.newSingleThreadExecutor()) {
      // When
      val firstWorker = executor.submit(() -> workers.at(now).tick());
      try {
        assertTrue(
            sending.await(CONCURRENCY_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
            "first worker must reach delivery");
        workers.at(now).tick();
      } finally {
        release.countDown();
      }
      firstWorker.get(CONCURRENCY_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

      // Then
      verify(delivery)
          .sendEmailVerification(eq(account.registration().email()), anyString(), anyString());
      assertNotNull(mailJobs.findById(account.mailJobId()).orElseThrow().getCompletedAt());
    }
  }

  @Test
  void exhaustedRetriesRetainCiphertext() {
    // Given
    failDelivery();

    // When
    exhaustRetries();

    // Then
    val job = mailJobs.findById(account.mailJobId()).orElseThrow();
    assertEquals(IdentityMailJobStatus.FAILED, job.getStatus());
    assertFalse(job.getPayload().isEmpty());
    verify(delivery, times(properties.maxAttempts()))
        .sendEmailVerification(eq(account.registration().email()), anyString(), anyString());
  }

  @Test
  void expiredActionClearsFailedPayload() {
    // Given
    failDelivery();
    exhaustRetries();
    val token = new UserActionTokenEntity();
    token.setTokenHash(mailJobs.findById(account.mailJobId()).orElseThrow().getActionHash());
    val expiresAt = actions.findOne(Example.of(token)).orElseThrow().getExpiresAt();

    // When
    workers.at(expiresAt).tick();

    // Then
    assertEquals("", mailJobs.findById(account.mailJobId()).orElseThrow().getPayload());
    verify(delivery, times(properties.maxAttempts()))
        .sendEmailVerification(eq(account.registration().email()), anyString(), anyString());
  }

  private void failDelivery() {
    doThrow(new IllegalStateException("SMTP unavailable"))
        .when(delivery)
        .sendEmailVerification(anyString(), anyString(), anyString());
  }

  private void exhaustRetries() {
    Instant next = Instant.now();
    for (int attempt = 0; attempt < properties.maxAttempts(); attempt++) {
      workers.at(next).tick();
      next = mailJobs.findById(account.mailJobId()).orElseThrow().getNextAttemptAt();
    }
    workers.at(next).tick();
  }
}
