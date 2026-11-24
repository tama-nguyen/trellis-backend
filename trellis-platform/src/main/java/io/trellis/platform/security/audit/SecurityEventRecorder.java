package io.trellis.platform.security.audit;

import io.micrometer.core.instrument.MeterRegistry;
import io.trellis.common.context.RequestContext;
import io.trellis.common.id.UuidV7;
import io.trellis.common.security.SecurityAction;
import java.sql.Timestamp;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Fixed allowlist schema. Failure records survive the rejected business transaction. */
public final class SecurityEventRecorder {
  public enum Outcome {
    SUCCESS,
    REJECTED,
    ERROR
  }

  private static final Logger log = LoggerFactory.getLogger(SecurityEventRecorder.class);
  private static final String INSERT =
      """
 INSERT INTO security_event (id,occurred_at,actor_type,actor_id,action,outcome,error_code,correlation_id)
 VALUES (:id,:now,:actorType,:actorId,:action,:outcome,:errorCode,:correlationId)
 """;
  private final JdbcClient jdbc;
  private final TransactionTemplate transaction;
  private final Clock clock;
  private final MeterRegistry meters;

  public SecurityEventRecorder(
      JdbcClient jdbc, PlatformTransactionManager manager, Clock clock, MeterRegistry meters) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.meters = meters;
    transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.setTimeout(2);
  }

  public void record(RequestContext ctx, SecurityAction action, Outcome outcome, String errorCode) {
    try {
      transaction.executeWithoutResult(
          status ->
              jdbc.sql(INSERT)
                  .param("id", UuidV7.next(clock))
                  .param("now", Timestamp.from(clock.instant()))
                  .param("actorType", ctx.actor().type().name())
                  .param("actorId", ctx.actor().id())
                  .param("action", action.name())
                  .param("outcome", outcome.name())
                  .param("errorCode", errorCode)
                  .param("correlationId", ctx.correlationId())
                  .update());
    } catch (RuntimeException unavailable) {
      meters.counter("trellis.security.audit", "outcome", "unavailable").increment();
      log.atWarn()
          .addKeyValue("action", action.name())
          .addKeyValue("outcome", outcome.name())
          .addKeyValue("correlation_id", ctx.correlationId())
          .log("Security event persistence unavailable");
    }
  }
}
