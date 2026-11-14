/**
 * App mode {@code setup} (profile {@code setup}): the one-shot job compose and deployments run before the
 * backend. Liquibase applies the schema during startup; then this package creates Kafka topics and the object
 * storage bucket when asked, and the process exits 0 (1 on failure). Idempotent: re-running changes nothing.
 */
package io.trellis.app.setup;
