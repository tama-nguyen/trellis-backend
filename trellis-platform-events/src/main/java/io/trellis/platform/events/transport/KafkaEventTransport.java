package io.trellis.platform.events.transport;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.RetriableException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Sends a batch asynchronously and waits for all acknowledgements within the timeout. Safe for
 * ordering because a relay batch holds at most one event per key. Producer must be {@code
 * acks=all}, {@code enable.idempotence=true} (set by the events auto-configuration).
 */
public final class KafkaEventTransport implements EventTransport {
  public static final String NAME = "kafka";

  private final KafkaTemplate<String, byte[]> kafka;

  public KafkaEventTransport(KafkaTemplate<String, byte[]> kafka) {
    this.kafka = kafka;
  }

  @Override
  public List<SendOutcome> send(List<OutboundEvent> events, Duration timeout) {
    List<CompletableFuture<SendResult<String, byte[]>>> futures = new ArrayList<>(events.size());
    for (OutboundEvent event : events) {
      futures.add(sendOne(event));
    }
    long deadline = System.nanoTime() + timeout.toNanos();
    List<SendOutcome> outcomes = new ArrayList<>(events.size());
    for (CompletableFuture<SendResult<String, byte[]>> future : futures) {
      outcomes.add(await(future, Math.max(0, deadline - System.nanoTime())));
    }
    return outcomes;
  }

  @Override
  public String name() {
    return NAME;
  }

  private CompletableFuture<SendResult<String, byte[]>> sendOne(OutboundEvent event) {
    ProducerRecord<String, byte[]> record =
        new ProducerRecord<>(event.topic(), event.key(), event.value());
    event
        .headers()
        .forEach(
            (name, value) -> record.headers().add(name, value.getBytes(StandardCharsets.UTF_8)));
    try {
      return kafka.send(record);
    } catch (RuntimeException e) {
      // Synchronous failures (serialisation, metadata timeout on a dead cluster) surface as a
      // failed future.
      return CompletableFuture.failedFuture(e);
    }
  }

  private static SendOutcome await(
      CompletableFuture<SendResult<String, byte[]>> future, long nanos) {
    try {
      future.get(nanos, TimeUnit.NANOSECONDS);
      return new SendOutcome.Sent();
    } catch (TimeoutException e) {
      future.cancel(false);
      return new SendOutcome.TransportDown("send timed out");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new SendOutcome.TransportDown("interrupted");
    } catch (ExecutionException e) {
      return classify(e.getCause());
    }
  }

  private static SendOutcome classify(Throwable error) {
    Throwable current = error;
    while (current != null) {
      if (current instanceof RetriableException || current instanceof TimeoutException) {
        return new SendOutcome.TransportDown(current.toString());
      }
      current = current.getCause();
    }
    return new SendOutcome.RowRejected(String.valueOf(error));
  }
}
