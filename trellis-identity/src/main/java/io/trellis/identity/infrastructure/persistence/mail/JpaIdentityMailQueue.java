package io.trellis.identity.infrastructure.persistence.mail;

import io.trellis.identity.IdentityMailProperties;
import io.trellis.identity.application.port.IdentityMailQueue;
import io.trellis.identity.application.view.IdentityMailClaim;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class JpaIdentityMailQueue implements IdentityMailQueue {
  private final IdentityMailJobRepository jobs;
  private final IdentityMailProperties properties;

  public JpaIdentityMailQueue(IdentityMailJobRepository jobs, IdentityMailProperties properties) {
    this.jobs = jobs;
    this.properties = properties;
  }

  public Optional<IdentityMailClaim> claim(Instant now) {
    jobs.retireInvalid(now, properties.maxAttempts());
    jobs.clearInvalidTerminalPayloads(now);
    jobs.deleteCompleted(now.minus(Duration.ofDays(1)));
    return jobs.claimable(now, properties.maxAttempts())
        .map(
            job -> {
              job.setAttempts(job.getAttempts() + 1);
              job.setLeaseId(UUID.randomUUID());
              job.setLeaseUntil(now.plus(properties.lease()));
              job.setNextAttemptAt(
                  now.plus(properties.lease())
                      .plus(properties.retryDelay().multipliedBy(job.getAttempts())));
              return new IdentityMailClaim(
                  job.getId(), job.getLeaseId(), job.getKeyVersion(), job.getPayload());
            });
  }

  public void complete(IdentityMailClaim claim, Instant now) {
    jobs.complete(claim.id(), claim.lease(), now);
  }
}
