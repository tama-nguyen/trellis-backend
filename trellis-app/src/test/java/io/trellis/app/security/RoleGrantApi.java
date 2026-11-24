package io.trellis.app.security;

import io.trellis.app.platform.dto.rolegrant.RoleGrantRequest;
import io.trellis.app.platform.dto.rolegrant.RoleGrantView;
import io.trellis.app.support.TestApiClient;
import io.trellis.common.http.HttpHeaderNames;
import java.net.http.HttpResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
class RoleGrantApi {
  private static final String GRANTS = "/api/platform/role-grants";
  private final TestApiClient http;
  private final JsonMapper json;

  HttpResponse<String> grant(
      final int port, final String actor, final String key, final RoleGrantRequest payload)
      throws Exception {
    val headers = Map.of(HttpHeaderNames.IDEMPOTENCY_KEY, key);
    return http.request(port, HttpMethod.POST.name(), GRANTS, actor, payload, headers);
  }

  HttpResponse<String> revoke(final int port, final String actor, final long id) throws Exception {
    return http.request(port, HttpMethod.DELETE.name(), GRANTS + "/" + id, actor, null);
  }

  RoleGrantView body(final HttpResponse<String> response) {
    return json.readValue(response.body(), RoleGrantView.class);
  }
}
