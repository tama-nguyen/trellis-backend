package io.trellis.app.support;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;

@RequiredArgsConstructor
public class AuditProbe {
  private final JdbcClient jdbc;

  // SecurityEventRecorder exposes writes only; no production read repository exists for this table.
  public Map<String, Object> securityEvent(final String correlationId) {
    return jdbc.sql("SELECT * FROM security_event WHERE correlation_id=:id")
        .param("id", correlationId)
        .query()
        .singleRow();
  }
}
