package io.trellis.platform.events.transport;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.DisposableBean;

/**
 * Single-process broker for dev and tests. Keeps every topic's messages in memory (inspectable,
 * drainable for DLQ replay) and delivers to subscribers on one background thread, so delivery is
 * asynchronous like a real broker and per-topic order is preserved. Nothing survives a restart.
 */
public final class InMemoryEventTransport implements EventTransport, DisposableBean {
  public static final String NAME = "inmemory";

  private final Map<String, List<OutboundEvent>> topics = new ConcurrentHashMap<>();
  private final Map<String, List<Subscriber>> subscribers = new ConcurrentHashMap<>();
  private final ExecutorService delivery =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread thread = new Thread(r, "trellis-inmemory-events");
            thread.setDaemon(true);
            return thread;
          });

  @Override
  public List<SendOutcome> send(List<OutboundEvent> events, Duration timeout) {
    List<SendOutcome> outcomes = new ArrayList<>(events.size());
    for (OutboundEvent event : events) {
      topics.computeIfAbsent(event.topic(), t -> new CopyOnWriteArrayList<>()).add(event);
      for (Subscriber subscriber : subscribers.getOrDefault(event.topic(), List.of())) {
        delivery.execute(() -> subscriber.accept(event));
      }
      outcomes.add(new SendOutcome.Sent());
    }
    return outcomes;
  }

  @Override
  public String name() {
    return NAME;
  }

  public void subscribe(String topic, Subscriber subscriber) {
    subscribers.computeIfAbsent(topic, t -> new CopyOnWriteArrayList<>()).add(subscriber);
  }

  /** Everything ever sent to {@code topic}, oldest first. */
  public List<OutboundEvent> messages(String topic) {
    return List.copyOf(topics.getOrDefault(topic, List.of()));
  }

  /** Removes and returns up to {@code max} messages of {@code topic} (used to replay a DLQ). */
  public synchronized List<OutboundEvent> drain(String topic, int max) {
    List<OutboundEvent> stored = topics.getOrDefault(topic, List.of());
    List<OutboundEvent> taken = new ArrayList<>(stored.subList(0, Math.min(max, stored.size())));
    stored.removeAll(taken);
    return taken;
  }

  @Override
  public void destroy() throws InterruptedException {
    delivery.shutdown();
    delivery.awaitTermination(5, TimeUnit.SECONDS);
  }
}
