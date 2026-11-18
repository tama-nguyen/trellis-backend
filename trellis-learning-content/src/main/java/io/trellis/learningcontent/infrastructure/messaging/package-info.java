/**
 * Event consumers ({@code EventHandler}) and read-model rebuilders of the module. Write through {@code
 * application.port}; the platform supplies the transaction, inbox, retries and DLQ. Empty until the module
 * consumes an event or owns a read model ({@code trellis-notice} is the live example of both).
 */
package io.trellis.learningcontent.infrastructure.messaging;
