package io.trellis.app.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;

@RequiredArgsConstructor
class NotificationPersistenceProbe {
  private final JdbcClient jdbc;

  // Reproduce the operator replay operation while retaining real relay/inbox deduplication.
  void replay(final String code) {
    jdbc.sql("UPDATE outbox_event SET status='NEW',next_attempt_at=now() WHERE aggregate_id=:code")
        .param("code", code)
        .update();
  }

  long outboxRows(final String code) {
    return jdbc.sql(
            "SELECT count(*) FROM outbox_event WHERE aggregate_id=:code AND event_type='notification.published'")
        .param("code", code)
        .query(Long.class)
        .single();
  }
}
