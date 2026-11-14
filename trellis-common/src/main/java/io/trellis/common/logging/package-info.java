/**
 * Role: the keys of structured logging - the MDC/log field names ({@link io.trellis.common.logging.LogKeys})
 *     that every log line, filter and dispatcher uses, so a log search means the same thing everywhere.
 * May depend on: the JDK only.
 * Must not depend on: Spring, slf4j bindings, {@code io.trellis.platform..}.
 */
package io.trellis.common.logging;
