package io.trellis.platform.persistence;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.trellis.common.page.CursorCodec;
import io.trellis.platform.persistence.keyset.PaginationProperties;
import io.trellis.platform.persistence.tx.RetryingTransactions;
import io.trellis.platform.persistence.tx.TransactionRetryProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Wires the persistence helpers. Present on the classpath means active; there is nothing to switch
 * on.
 */
@AutoConfiguration(after = DataSourceTransactionManagerAutoConfiguration.class)
@Import({TransactionRetryProperties.class, PaginationProperties.class})
public class PersistenceAutoConfiguration {
  private static final Logger log = LoggerFactory.getLogger(PersistenceAutoConfiguration.class);

  /** Shared cursor codec for every keyset list API; signed when a secret is configured. */
  @Bean
  @ConditionalOnMissingBean
  CursorCodec cursorCodec(final PaginationProperties properties, final Environment environment) {
    final String secret = properties.cursorSecret();
    if (secret == null || secret.isBlank()) {
      if (environment.matchesProfiles("prod")) {
        throw new IllegalStateException("Production requires trellis.pagination.cursor-secret");
      }
      log.warn("trellis.pagination.cursor-secret is empty: page cursors are unsigned");
      return CursorCodec.unsigned();
    }
    return CursorCodec.signed(secret.getBytes(StandardCharsets.UTF_8));
  }

  @Bean
  @ConditionalOnMissingBean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnMissingBean
  RetryingTransactions retryingTransactions(
      PlatformTransactionManager transactionManager,
      ObjectProvider<MeterRegistry> meterRegistry,
      TransactionRetryProperties properties) {
    return new RetryingTransactions(
        transactionManager, meterRegistry.getIfAvailable(SimpleMeterRegistry::new), properties);
  }
}
