package io.trellis.app.notice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import io.micrometer.core.instrument.MeterRegistry;
import io.trellis.app.PostgresTestcontainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The template's reference flow, end to end inside the backend (platform verification, decision
 * T5): REST publish writes the notice and its outbox row in one transaction, the relay delivers
 * {@code notice.published} through the in-memory transport, the Java projection updates {@code
 * notice_stats} exactly once (also when the event is replayed), a repeated {@code Idempotency-Key}
 * returns the first result, GraphQL serves the read model with batched stats, and a STAFF notice is
 * a 404 for a non-staff reader. Tokens are real RS256 JWTs checked by the platform's resource
 * server, signed by a key created for this test.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"trellis.events.relay.enabled=true", "trellis.events.relay.poll-interval=PT0.1S"})
@ActiveProfiles("test")
@Import(PostgresTestcontainer.class)
class NoticeFlowIT {
  private static final RSAKey SIGNING_KEY = newKey();
  private static final Duration WAIT = Duration.ofSeconds(15);
  private static final String ADMIN = "notice-admin";
  private static final String READER = "notice-reader";
  private static final String STAFF = "staff";
  private static final String PROJECTION = "notice-stats-projection";
  private static final String INBOX_DUPLICATE_METRIC = "inbox.duplicate";

  @TestBean private JwtDecoder jwtDecoder;

  @LocalServerPort private int port;

  @Autowired private JdbcClient jdbc;

  @Autowired private MeterRegistry meters;

  private final HttpClient http = HttpClient.newHttpClient();
  private final JsonMapper json = JsonMapper.builder().build();

  static JwtDecoder jwtDecoder() {
    try {
      return NimbusJwtDecoder.withPublicKey(SIGNING_KEY.toRSAPublicKey()).build();
    } catch (com.nimbusds.jose.JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void publishFlowProjectsOnceReplaysIdempotentlyAndHidesStaffNotices() throws Exception {
    String admin = token("admin-1", ADMIN);
    String reader = token("reader-1", READER);
    String staff = token("staff-1", STAFF);

    // Create + publish an ALL notice, with an Idempotency-Key.
    String code = create(admin, "ALL");
    HttpResponse<String> first =
        send(
            post("/api/notices/" + code + "/publish", admin, "")
                .header("Idempotency-Key", "publish-1")
                .build());
    assertEquals(200, first.statusCode(), first.body());
    assertEquals(1L, outboxRows(code), "publish writes exactly one outbox row in its transaction");

    // Relay (in-memory) -> NoticeStatsProjection -> notice_stats.
    await(() -> publishCount(code) == 1, "notice_stats counts the publication");

    // Same key again: the stored first result, not "already published", and no second event.
    HttpResponse<String> replay =
        send(
            post("/api/notices/" + code + "/publish", admin, "")
                .header("Idempotency-Key", "publish-1")
                .build());
    assertEquals(200, replay.statusCode(), replay.body());
    assertEquals(json.readTree(first.body()), json.readTree(replay.body()));
    assertEquals(1L, outboxRows(code));

    // Re-deliver the published event (runbook "replay a published range"): the inbox dedupes it.
    double duplicatesBefore = projectionDuplicates();
    jdbc.sql(
            "UPDATE outbox_event SET status = 'NEW', next_attempt_at = now() WHERE aggregate_id = :code")
        .param("code", code)
        .update();
    await(
        () -> projectionDuplicates() > duplicatesBefore,
        "the projection receives the replayed event");
    assertEquals(1, publishCount(code), "a replayed event is counted once");

    // GraphQL read model: the notice with its batched stats.
    JsonNode gql =
        graphql(reader, "{ notices(first: 50) { items { code stats { publishCount } } hasNext } }");
    JsonNode item = findByCode(gql.path("data").path("notices").path("items"), code);
    assertEquals(1, item.path("stats").path("publishCount").asInt(), gql.toString());

    // STAFF notice: 404 (never 403) for a reader without notice:read-staff, visible to staff.
    String staffCode = create(admin, "STAFF");
    assertEquals(
        200, send(post("/api/notices/" + staffCode + "/publish", admin, "").build()).statusCode());
    HttpResponse<String> hidden = send(get("/api/notices/" + staffCode, reader));
    assertEquals(404, hidden.statusCode(), hidden.body());
    assertEquals("NOTICE_NOT_FOUND", json.readTree(hidden.body()).path("code").asString());
    assertEquals(200, send(get("/api/notices/" + staffCode, staff)).statusCode());
    JsonNode hiddenGql = graphql(reader, "{ notice(code: \"" + staffCode + "\") { code } }");
    assertEquals(
        "NOT_FOUND", hiddenGql.path("errors").path(0).path("extensions").path("code").asString());
    assertTrue(
        findByCode(
                graphql(reader, "{ notices(first: 50) { items { code } } }")
                    .path("data")
                    .path("notices")
                    .path("items"),
                staffCode)
            .isMissingNode(),
        "a STAFF notice is not listed for a reader");
  }

  private String create(String token, String audience) throws Exception {
    String body =
        json.writeValueAsString(
            Map.of(
                "title",
                "Maintenance window " + audience,
                "body",
                "Sample body.",
                "severity",
                "INFO",
                "audience",
                audience));
    HttpResponse<String> created = send(post("/api/notices", token, body).build());
    assertEquals(201, created.statusCode(), created.body());
    return json.readTree(created.body()).path("code").asString();
  }

  private JsonNode graphql(String token, String query) throws Exception {
    HttpResponse<String> response =
        send(post("/api/graphql", token, json.writeValueAsString(Map.of("query", query))).build());
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body());
  }

  private static JsonNode findByCode(JsonNode items, String code) {
    for (JsonNode item : items) {
      if (code.equals(item.path("code").asString())) {
        return item;
      }
    }
    return json().missingNode();
  }

  private static JsonMapper json() {
    return JsonMapper.builder().build();
  }

  private long outboxRows(String code) {
    return jdbc.sql(
            "SELECT count(*) FROM outbox_event WHERE aggregate_id = :code AND event_type = 'notice.published'")
        .param("code", code)
        .query(Long.class)
        .single();
  }

  private int publishCount(String code) {
    return jdbc.sql(
            "SELECT coalesce(max(publish_count), 0) FROM notice_stats WHERE notice_code = :code")
        .param("code", code)
        .query(Integer.class)
        .single();
  }

  private double projectionDuplicates() {
    var counter = meters.find(INBOX_DUPLICATE_METRIC).tag("consumer", PROJECTION).counter();
    return counter == null ? 0 : counter.count();
  }

  private static void await(BooleanSupplier condition, String what) throws InterruptedException {
    Instant deadline = Instant.now().plus(WAIT);
    while (!condition.getAsBoolean()) {
      if (Instant.now().isAfter(deadline)) {
        throw new AssertionError("Timed out waiting until " + what);
      }
      Thread.sleep(100);
    }
  }

  private HttpRequest.Builder post(String path, String token, String body) {
    return HttpRequest.newBuilder(uri(path))
        .header("Authorization", "Bearer " + token)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body));
  }

  private HttpRequest get(String path, String token) {
    return HttpRequest.newBuilder(uri(path))
        .header("Authorization", "Bearer " + token)
        .GET()
        .build();
  }

  private HttpResponse<String> send(HttpRequest request) throws Exception {
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  private static String token(String subject, String role) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofMinutes(10)))
            .claim("realm_access", Map.of("roles", List.of(role)))
            .build();
    NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(SIGNING_KEY)));
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  private static RSAKey newKey() {
    try {
      return new RSAKeyGenerator(2048).generate();
    } catch (com.nimbusds.jose.JOSEException e) {
      throw new IllegalStateException(e);
    }
  }
}
