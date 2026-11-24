package io.trellis.platform.events.outbox;

import io.trellis.common.context.RequestContext;
import java.util.UUID;

/**
 * Appends an event to the outbox inside the caller's transaction. Never opens a transaction:
 * calling it without one fails ({@code IllegalTransactionStateException}), because an event written
 * outside the business transaction can be published for a change that rolled back.
 */
public interface OutboxWriter {
  /**
   * @return the generated event id (UUIDv7)
   */
  UUID append(RequestContext ctx, OutboxMessage message);
}
