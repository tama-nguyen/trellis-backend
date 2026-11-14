/**
 * Data backfills that are not schema changes (decision T10, after DataHub's {@code datahub-upgrade}).
 * Liquibase owns DDL; a {@link io.trellis.platform.persistence.migration.MigrationStep} owns re-shaping data
 * in resumable batches with a checkpoint row. Blocking steps run in the {@code migrate} app mode before a
 * release takes traffic; non-blocking steps may run in the background of the serving app.
 */
package io.trellis.platform.persistence.migration;
