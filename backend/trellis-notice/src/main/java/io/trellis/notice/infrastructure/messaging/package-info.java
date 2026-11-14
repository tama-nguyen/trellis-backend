/**
 * Event consumers ({@code EventHandler}) and read-model rebuilders of the module. Write through {@code
 * application.port}; the platform supplies the transaction, inbox, retries and DLQ.
 */
package io.trellis.notice.infrastructure.messaging;
