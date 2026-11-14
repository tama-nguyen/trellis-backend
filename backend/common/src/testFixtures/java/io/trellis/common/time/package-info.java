/**
 * Role: test fixtures for time - {@link io.trellis.common.time.MutableClock}, the controllable
 *     {@link java.time.Clock} that tests and dev tools inject instead of the system clock. Production code
 *     takes a {@code Clock} bean and never calls {@code Instant.now()} (ArchUnit enforces it).
 * May depend on: the JDK only.
 * Must not depend on: anything; it ships in the test-fixtures jar, never in a runtime one.
 */
package io.trellis.common.time;
