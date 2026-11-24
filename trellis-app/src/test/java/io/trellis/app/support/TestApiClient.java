package io.trellis.app.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.val;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
public class TestApiClient {

  private final JsonMapper json;

  public HttpResponse<String> request(
      final int port,
      final String method,
      final String path,
      final String bearer,
      final Object body)
      throws Exception {

    return request(port, method, path, bearer, body, Map.of());
  }

  public HttpResponse<String> request(
      final int port,
      final String method,
      final String path,
      final String bearer,
      final Object body,
      final Map<String, String> headers)
      throws Exception {
    val builder = HttpRequest.newBuilder(uri(port, path));

    if (bearer != null) {
      builder.header("Authorization", "Bearer " + bearer);
    }

    if (body != null) {
      builder.header("Content-Type", "application/json");
    }

    headers.forEach(builder::header);
    builder.method(method, bodyPublisher(body));

    return send(builder.build());
  }

  public HttpResponse<String> send(final HttpRequest request) throws Exception {
    try (val client = HttpClient.newHttpClient()) {
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
  }

  public JsonNode assertJsonBody(final HttpResponse<String> response, final int expectedStatus) {

    assertEquals(expectedStatus, response.statusCode(), response.body());

    return json.readTree(response.body());
  }

  public String json(final Object payload) {
    return json.writeValueAsString(payload);
  }

  public String login(final int port, final UUID userId) throws Exception {
    val credentials =
        Map.of("email", IdentityFixtures.email(userId), "password", IdentityFixtures.PASSWORD);

    val response = request(port, "POST", "/api/auth/login", null, credentials);

    val responseBody = assertJsonBody(response, 200);

    return responseBody.path("accessToken").asString();
  }

  private HttpRequest.BodyPublisher bodyPublisher(final Object body) {
    if (body == null) {
      return HttpRequest.BodyPublishers.noBody();
    }

    val payload = body instanceof String text ? text : json.writeValueAsString(body);

    return HttpRequest.BodyPublishers.ofString(payload);
  }

  private URI uri(final int port, final String path) {
    return URI.create("http://localhost:" + port + path);
  }
}
