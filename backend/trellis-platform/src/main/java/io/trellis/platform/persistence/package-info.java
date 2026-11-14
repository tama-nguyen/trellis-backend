/**
 * Persistence platform: native SQL over Spring's {@code JdbcClient} (no JPA, decision T10), Liquibase schema
 * migrations, {@link io.trellis.platform.persistence.tx.RetryingTransactions} for optimistic-lock retries,
 * {@link io.trellis.platform.persistence.keyset.KeysetQuery} for cursor paging, and the {@link
 * io.trellis.platform.persistence.migration.MigrationStep} backfill framework.
 */
package io.trellis.platform.persistence;
