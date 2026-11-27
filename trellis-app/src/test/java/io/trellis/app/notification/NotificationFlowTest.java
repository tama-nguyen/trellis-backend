package io.trellis.app.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.MeterRegistry;
import io.trellis.app.PostgresTestcontainer;
import io.trellis.app.support.TestApiClient;
import io.trellis.app.support.TestJwtIssuer;
import io.trellis.common.http.HttpHeaderNames;
import io.trellis.notification.api.dto.NotificationAudience;
import io.trellis.notification.api.dto.NotificationSeverity;
import io.trellis.notification.application.port.NotificationStatsRepository;
import io.trellis.notification.application.view.NotificationStatsView;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.convention.TestBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real HTTP, PostgreSQL, outbox relay and inbox deduplication for the reference notification flow.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"trellis.events.relay.enabled=true", "trellis.events.relay.poll-interval=PT0.1S"})
@ActiveProfiles("test")
@Import({PostgresTestcontainer.class, NotificationPersistenceProbe.class, TestApiClient.class})
@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class NotificationFlowTest {
  private static final Duration WAIT = Duration.ofSeconds(15);
  private static final String NOTIFICATIONS = "/api/notifications";
  private static final String GRAPHQL = "/api/graphql";
  private static final String ADMIN_ROLE = "notification-admin";
  private static final String READER_ROLE = "notification-reader";
  private static final String STAFF_ROLE = "staff";
  private static final String PROJECTION = "notification-stats-projection";
  private static final String DUPLICATE_METRIC = "inbox.duplicate";
  private static final String LIST_QUERY =
      "{ notifications(first: 50) { items { code stats { publishCount } } } }";
  private static final String SCOPED_QUERY =
      """
      query StaffVisibility($code: ID!) {
        notification(code: $code) { code }
        notifications(first: 50) { items { code } }
      }
      """;

  @TestBean private JwtDecoder jwtDecoder;
  @LocalServerPort private int port;
  private final TestApiClient http;
  private final JsonMapper json;
  private final NotificationPersistenceProbe persistence;
  private final NotificationStatsRepository stats;
  private final MeterRegistry meters;

  static JwtDecoder jwtDecoder() throws Exception {
    return TestJwtIssuer.decoder();
  }

  @Test
  void publishingProjectsOneEventAndExposesItsStats() throws Exception {
    // Given
    val administrator = token(ADMIN_ROLE);
    val code = createDraft(administrator, NotificationAudience.ALL);

    // When
    val response = publish(administrator, code, UUID.randomUUID().toString());

    // Then
    assertEquals(HttpStatus.OK.value(), response.statusCode());
    assertEquals(1L, persistence.outboxRows(code));
    await(() -> publishCount(code) == 1, "notification publication projection");
    val projected = graphQl(token(READER_ROLE), LIST_QUERY, Map.of());
    assertEquals(HttpStatus.OK.value(), projected.statusCode());
    val items = json.readTree(projected.body()).path("data").path("notifications").path("items");
    val item = findByCode(items, code);
    assertEquals(1, item.path("stats").path("publishCount").asInt());
  }

  @Test
  void repeatingPublishReturnsStoredResultWithoutAnotherEvent() throws Exception {
    // Given
    val published = published(NotificationAudience.ALL);

    // When
    val replay = publish(published.actor(), published.code(), published.key());

    // Then
    assertEquals(HttpStatus.OK.value(), replay.statusCode());
    assertEquals(json.readTree(published.response().body()), json.readTree(replay.body()));
    assertEquals(1L, persistence.outboxRows(published.code()));
  }

  @Test
  void replayedOutboxEventIsDeduplicatedByTheProjection() throws Exception {
    // Given
    val published = published(NotificationAudience.ALL);
    await(() -> publishCount(published.code()) == 1, "initial publication projection");
    val beforeReplay = projectionDuplicates();

    // When
    persistence.replay(published.code());

    // Then
    await(() -> projectionDuplicates() > beforeReplay, "inbox duplicate observation");
    assertEquals(1, publishCount(published.code()));
  }

  @Test
  void ordinaryReaderCannotRetrieveStaffNotification() throws Exception {
    // Given
    val published = published(NotificationAudience.STAFF);
    val reader = token(READER_ROLE);

    // When
    val response =
        http.request(port, HttpMethod.GET.name(), notificationPath(published.code()), reader, null);

    // Then
    assertEquals(HttpStatus.NOT_FOUND.value(), response.statusCode());
    assertEquals("NOTIFICATION_NOT_FOUND", json.readTree(response.body()).path("code").asString());
  }

  @Test
  void staffReaderCanRetrieveStaffNotification() throws Exception {
    // Given
    val published = published(NotificationAudience.STAFF);
    val staff = token(STAFF_ROLE);

    // When
    val response =
        http.request(port, HttpMethod.GET.name(), notificationPath(published.code()), staff, null);

    // Then
    assertEquals(HttpStatus.OK.value(), response.statusCode());
    assertEquals(published.code(), json.readTree(response.body()).path("code").asString());
  }

  @Test
  void graphQlHidesStaffNotificationFromDetailAndList() throws Exception {
    // Given
    val published = published(NotificationAudience.STAFF);
    val reader = token(READER_ROLE);

    // When
    val response = graphQl(reader, SCOPED_QUERY, Map.of("code", published.code()));

    // Then
    assertEquals(HttpStatus.OK.value(), response.statusCode());
    val body = json.readTree(response.body());
    val error = body.path("errors").path(0).path("extensions").path("code");
    assertEquals("NOT_FOUND", error.asString());
    val items = body.path("data").path("notifications").path("items");
    assertTrue(findByCode(items, published.code()).isMissingNode());
  }

  private String createDraft(final String actor, final NotificationAudience audience)
      throws Exception {
    val details =
        new NotificationRequest(
            "Maintenance window", "Sample body.", NotificationSeverity.INFO, audience);
    val response = http.request(port, HttpMethod.POST.name(), NOTIFICATIONS, actor, details);
    val created = http.assertJsonBody(response, HttpStatus.CREATED.value());
    return created.path("code").asString();
  }

  private PublishedNotification published(final NotificationAudience audience) throws Exception {
    val actor = token(ADMIN_ROLE);
    val code = createDraft(actor, audience);
    val key = UUID.randomUUID().toString();
    val response = publish(actor, code, key);
    assertEquals(HttpStatus.OK.value(), response.statusCode(), "published fixture must commit");
    return new PublishedNotification(code, actor, key, response);
  }

  private HttpResponse<String> publish(final String actor, final String code, final String key)
      throws Exception {
    val headers = Map.of(HttpHeaderNames.IDEMPOTENCY_KEY, key);
    return http.request(
        port, HttpMethod.POST.name(), notificationPath(code) + "/publish", actor, null, headers);
  }

  private HttpResponse<String> graphQl(
      final String actor, final String query, final Map<String, Object> variables)
      throws Exception {
    return http.request(
        port, HttpMethod.POST.name(), GRAPHQL, actor, new GraphQlRequest(query, variables));
  }

  private JsonNode findByCode(final JsonNode items, final String code) {
    for (val item : items) {
      if (code.equals(item.path("code").asString())) {
        return item;
      }
    }
    return json.missingNode();
  }

  private int publishCount(final String code) {
    return stats
        .findByCodes(List.of(code))
        .getOrDefault(code, NotificationStatsView.empty(code))
        .publishCount();
  }

  private double projectionDuplicates() {
    val counter = meters.find(DUPLICATE_METRIC).tag("consumer", PROJECTION).counter();
    return counter == null ? 0 : counter.count();
  }

  private void await(final BooleanSupplier condition, final String observation) {
    Awaitility.await(observation).atMost(WAIT).until(condition::getAsBoolean);
  }

  private String notificationPath(final String code) {
    return NOTIFICATIONS + "/" + code;
  }

  private String token(final String role) {
    return TestJwtIssuer.jwt(UUID.randomUUID().toString(), role);
  }

  private record NotificationRequest(
      String title, String body, NotificationSeverity severity, NotificationAudience audience) {}

  private record GraphQlRequest(String query, Map<String, Object> variables) {}

  private record PublishedNotification(
      String code, String actor, String key, HttpResponse<String> response) {}
}
