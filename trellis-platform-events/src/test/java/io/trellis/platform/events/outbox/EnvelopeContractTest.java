package io.trellis.platform.events.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.trellis.platform.events.EventHeaders;
import io.trellis.platform.events.envelope.EventEnvelope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import lombok.val;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contract test: what the relay puts on the wire must validate against {@code
 * contracts/events/envelope.schema.json} (with format assertions on), and the shared example must
 * round-trip through the Java record unchanged.
 */
class EnvelopeContractTest {
  private static final Path CONTRACTS = Path.of(System.getProperty("trellis.contracts.dir"));
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Test
  void relayedEnvelopeValidatesAgainstSchema() throws IOException {
    val schema =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(Files.readString(CONTRACTS.resolve("events/envelope.schema.json")));
    val row =
        new PendingRow(
            1L,
            UUID.fromString("01a1d97f-e500-7d85-a591-84b62830fb11"),
            "notification.published",
            1,
            "Notification",
            "NT-7K2M-9QXA-4P8R",
            "Notification:NT-7K2M-9QXA-4P8R",
            "notification.published",
            Files.readString(CONTRACTS.resolve("examples/notification.published.v1.json"))
                .transform(EnvelopeContractTest::payloadOf),
            "{\"correlationId\":\"req-3f9c2a7d\",\"traceparent\":\"00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01\","
                + "\"actorType\":\"USER\",\"actorId\":\"admin@example.test\"}",
            Instant.parse("2026-11-14T10:00:00Z"),
            0);

    val outbound = new EnvelopeCodec(JSON, "trellis-backend").toOutbound(row);

    val errors =
        schema.validate(
            JSON.readTree(outbound.value()),
            ctx -> ctx.executionConfig(c -> c.formatAssertionsEnabled(true)));
    assertTrue(errors.isEmpty(), () -> "Envelope violates the contract: " + errors);
    assertEquals(
        "01a1d97f-e500-7d85-a591-84b62830fb11", outbound.headers().get(EventHeaders.EVENT_ID));
    assertEquals("1", outbound.headers().get(EventHeaders.EVENT_VERSION));
    assertEquals("req-3f9c2a7d", outbound.headers().get(EventHeaders.CORRELATION_ID));
  }

  @Test
  void sharedExampleRoundTripsThroughTheRecord() throws IOException {
    val example =
        JSON.readTree(
            Files.readString(CONTRACTS.resolve("examples/notification.published.v1.json")));
    val envelope = JSON.treeToValue(example, EventEnvelope.class);
    assertEquals(example, JSON.valueToTree(envelope));
  }

  private static String payloadOf(final String exampleJson) {
    return JSON.readTree(exampleJson).get("payload").toString();
  }
}
