package io.trellis.platform.events.consumer;

import io.trellis.platform.events.outbox.TopicResolver;
import io.trellis.platform.events.transport.InMemoryEventTransport;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

/** Subscribes every enabled handler to the in-memory broker. */
public final class InMemoryConsumers implements SmartInitializingSingleton {
  private static final Logger log = LoggerFactory.getLogger(InMemoryConsumers.class);

  private final InMemoryEventTransport broker;
  private final EventDispatcher dispatcher;
  private final List<EventHandler<?>> handlers;
  private final TopicResolver topics;

  public InMemoryConsumers(
      InMemoryEventTransport broker,
      EventDispatcher dispatcher,
      List<EventHandler<?>> handlers,
      TopicResolver topics) {
    this.broker = broker;
    this.dispatcher = dispatcher;
    this.handlers = handlers;
    this.topics = topics;
  }

  @Override
  public void afterSingletonsInstantiated() {
    handlers.stream()
        .filter(EventHandler::isEnabled)
        .sorted(Comparator.comparingInt(EventHandler::order))
        .forEach(
            handler ->
                broker.subscribe(
                    topics.topicFor(handler.eventType()),
                    event -> {
                      try {
                        dispatcher.deliver(
                            handler,
                            new IncomingMessage(
                                event.topic(),
                                -1,
                                -1,
                                event.key(),
                                event.value(),
                                event.headers()));
                      } catch (EventHandlingHaltedException e) {
                        // In memory there is no redelivery; the halt is logged by the dispatcher.
                        log.atError()
                            .addKeyValue("consumer", handler.consumerName())
                            .log("In-memory consumer halted");
                      }
                    }));
  }
}
