package io.trellis.app.support;

import io.trellis.identity.application.view.AuthTokens;
import io.trellis.identity.application.view.AuthenticatedUser;
import java.net.http.HttpResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** HTTP driver: transport and wire payloads only; assertions stay in the test. */
@RequiredArgsConstructor
public class IdentityApi {
  private static final String AUTH = "/api/auth";
  private final TestApiClient http;
  private final JsonMapper json;

  public HttpResponse<String> register(final int port, final Registration account)
      throws Exception {
    return post(port, "/register", account);
  }

  public HttpResponse<String> login(final int port, final String email, final String password)
      throws Exception {
    return post(port, "/login", new Credentials(email, password));
  }

  public HttpResponse<String> refresh(final int port, final String refreshToken) throws Exception {
    return post(port, "/refresh", new RefreshToken(refreshToken));
  }

  public HttpResponse<String> logout(final int port, final String refreshToken) throws Exception {
    return post(port, "/logout", new RefreshToken(refreshToken));
  }

  public HttpResponse<String> profile(final int port, final String bearer) throws Exception {
    return http.request(port, HttpMethod.GET.name(), AUTH + "/me", bearer, null);
  }

  public HttpResponse<String> forgotPassword(final int port, final String email) throws Exception {
    return post(port, "/forgot-password", new Email(email));
  }

  public HttpResponse<String> resetPassword(
      final int port, final String email, final String code, final String password)
      throws Exception {
    return post(port, "/reset-password", new PasswordReset(email, code, password));
  }

  public HttpResponse<String> verifyEmail(final int port, final String token) throws Exception {
    return post(port, "/verify-email", new Verification(token));
  }

  public AuthTokens tokens(final HttpResponse<String> response) {
    return json.readValue(response.body(), AuthTokens.class);
  }

  public AuthenticatedUser user(final HttpResponse<String> response) {
    return json.readValue(response.body(), AuthenticatedUser.class);
  }

  public JsonNode document(final HttpResponse<String> response) {
    return json.readTree(response.body());
  }

  private HttpResponse<String> post(final int port, final String path, final Object payload)
      throws Exception {
    return http.request(port, HttpMethod.POST.name(), AUTH + path, null, payload);
  }

  public record Registration(String email, String password, String displayName) {}

  private record Credentials(String email, String password) {}

  private record RefreshToken(String refreshToken) {}

  private record Email(String email) {}

  private record PasswordReset(String email, String code, String newPassword) {}

  private record Verification(String token) {}
}
