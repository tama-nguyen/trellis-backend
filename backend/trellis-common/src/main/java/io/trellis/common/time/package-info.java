/**
 * Time. The abstraction is {@link java.time.Clock}, injected as a bean; code never calls {@code Instant.now()}
 * (ArchUnit enforces it). {@link io.trellis.common.time.MutableClock} is for tests and dev tools.
 */
package io.trellis.common.time;
