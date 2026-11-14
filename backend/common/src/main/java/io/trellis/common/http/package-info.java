/**
 * Role: HTTP vocabulary shared by every edge and client - the header names Trellis reads and writes
 *     ({@link io.trellis.common.http.HttpHeaderNames}), standard ones included.
 * May depend on: the JDK only.
 * Must not depend on: Spring, Jakarta, {@code io.trellis.platform..} - a worker or a test builds the same
 *     header names without a servlet container.
 */
package io.trellis.common.http;
