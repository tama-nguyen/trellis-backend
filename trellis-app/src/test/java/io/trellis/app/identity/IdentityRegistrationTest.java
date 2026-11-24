package io.trellis.app.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.trellis.app.support.AuditProbe;
import io.trellis.app.support.IdentityApi;
import io.trellis.app.support.IdentityFixtures;
import io.trellis.app.support.IdentityRegistrationFixtures;
import io.trellis.app.support.MailWorkerFixture;
import io.trellis.app.support.SecurityTestContext;
import io.trellis.identity.application.port.IdentityMailDelivery;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.usecase.registration.RegisterUseCase;
import io.trellis.identity.infrastructure.persistence.mail.IdentityMailJobEntity;
import io.trellis.identity.infrastructure.persistence.mail.IdentityMailJobRepository;
import io.trellis.platform.audit.AuditEventFilter;
import io.trellis.platform.audit.AuditEventRecord;
import io.trellis.platform.audit.AuditEventStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

@SecurityTestContext
@RequiredArgsConstructor
class IdentityRegistrationTest {
  @LocalServerPort private int port;

  private final IdentityRegistrationFixtures fixtures;
  private final IdentityFixtures transactions;
  private final IdentityApi api;
  private final RegisterUseCase register;
  private final UserRepository users;
  private final IdentityMailJobRepository mailJobs;
  private final IdentityMailDelivery delivery;
  private final MailWorkerFixture workers;
  private final AuditEventStore auditEvents;
  private final AuditProbe securityEvents;
  private final Set<UUID> ownedJobs = new HashSet<>();

  @AfterEach
  void removeOwnedMailJobs() {
    mailJobs.deleteAllById(ownedJobs);
  }

  @Test
  void registrationQueuesEncryptedMail() throws Exception {
    // Given
    val account = fixtures.registration();
    val existingJobs = jobIds();

    // When
    val response = api.register(port, account);

    // Then
    assertEquals(HttpStatus.CREATED.value(), response.statusCode());
    val added = jobIds();
    added.removeAll(existingJobs);
    ownedJobs.addAll(added);
    assertEquals(1, added.size());
    val encrypted = mailJobs.findById(added.iterator().next()).orElseThrow().getPayload();
    assertFalse(encrypted.contains(account.email()));
    assertFalse(encrypted.contains("tr_ac_"));
    verifyNoInteractions(delivery);
  }

  @Test
  void duplicateRegistrationHasNoAdditionalSuccessEffects() throws Exception {
    // Given
    val pending = fixtures.pendingAccount();
    ownedJobs.add(pending.mailJobId());
    val existingJobs = jobIds();
    val registration =
        new AuditEventFilter("Identity", pending.userId().toString(), null, null, null);
    val before = auditRecords(registration);

    // When
    val response = api.register(port, pending.registration());

    // Then
    assertEquals(HttpStatus.CONFLICT.value(), response.statusCode());
    assertEquals(existingJobs, jobIds());
    assertEquals(before, auditRecords(registration));
    val correlation = response.headers().firstValue("X-Correlation-Id").orElseThrow();
    assertTrue(
        auditRecords(new AuditEventFilter(null, null, null, null, null)).stream()
            .noneMatch(
                event ->
                    correlation.equals(event.correlationId())
                        && "identity.register".equals(event.action())));
    val rejection = securityEvents.securityEvent(correlation);
    assertEquals("REGISTER", rejection.get("action"));
    assertEquals("REJECTED", rejection.get("outcome"));
  }

  @Test
  void rollbackRemovesUserAndMailJob() {
    // Given
    val account = fixtures.registration();
    val existingJobs = jobIds();
    val userId = new AtomicReference<UUID>();

    // When
    val failure =
        assertThrows(IllegalStateException.class, () -> registerAndRollback(account, userId));

    // Then
    assertEquals("rollback registration fixture", failure.getMessage());
    assertNotNull(userId.get());
    assertTrue(users.userById(userId.get()).isEmpty());
    assertEquals(existingJobs, jobIds());
    workers.worker().tick();
    verifyNoInteractions(delivery);
  }

  @Test
  void emailedVerificationTokenEnablesSignIn() throws Exception {
    // Given
    val pending = fixtures.pendingAccount();
    ownedJobs.add(pending.mailJobId());
    workers.worker().tick();
    val token = ArgumentCaptor.forClass(String.class);
    verify(delivery)
        .sendEmailVerification(eq(pending.registration().email()), anyString(), token.capture());

    // When
    val response = api.verifyEmail(port, token.getValue());

    // Then
    assertEquals(HttpStatus.NO_CONTENT.value(), response.statusCode());
    assertEquals(
        HttpStatus.OK.value(),
        api.login(port, pending.registration().email(), pending.registration().password())
            .statusCode());
  }

  private void registerAndRollback(
      final IdentityApi.Registration account, final AtomicReference<UUID> userId) {
    transactions
        .tx()
        .executeWithoutResult(
            status -> {
              userId.set(
                  register.execute(
                      fixtures.context(),
                      account.email(),
                      account.password(),
                      account.displayName()));
              throw new IllegalStateException("rollback registration fixture");
            });
  }

  private Set<UUID> jobIds() {
    return mailJobs.findAll().stream()
        .map(IdentityMailJobEntity::getId)
        .collect(Collectors.toCollection(HashSet::new));
  }

  private List<AuditEventRecord> auditRecords(final AuditEventFilter filter) {
    val events = new ArrayList<AuditEventRecord>();
    String after = null;
    do {
      val page = auditEvents.page(filter, after, 50);
      events.addAll(page.items());
      after = page.nextCursor();
    } while (after != null);
    return events;
  }
}
