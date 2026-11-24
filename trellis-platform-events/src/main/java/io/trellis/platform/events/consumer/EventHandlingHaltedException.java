package io.trellis.platform.events.consumer;

/** A {@link FailureMode#THROW} handler gave up: the transport must not acknowledge the message. */
public class EventHandlingHaltedException extends RuntimeException {
  public EventHandlingHaltedException(String consumerName, Throwable cause) {
    super("Consumer " + consumerName + " halted", cause);
  }
}
