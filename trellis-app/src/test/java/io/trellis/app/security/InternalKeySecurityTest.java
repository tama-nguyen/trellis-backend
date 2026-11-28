package io.trellis.app.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.trellis.app.support.IdentityApi;
import io.trellis.app.support.IdentityFixtures;
import io.trellis.app.support.RoleFixtures;
import io.trellis.app.support.SecurityTestContext;
import io.trellis.app.support.TestApiClient;
import io.trellis.common.http.HttpHeaderNames;
import io.trellis.identity.domain.user.UserStatus;
import io.trellis.platform.security.jwt.OidcPrincipalIdEncoder;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

@SecurityTestContext
@TestPropertySource(properties = "trellis.security.internal-key=local-ai-test-secret")
@RequiredArgsConstructor
class InternalKeySecurityTest {
  private static final String INTERNAL_KEY = "local-ai-test-secret";
  @LocalServerPort private int port;
  private final TestApiClient api;
  private final IdentityApi identityApi;
  private final IdentityFixtures identities;
  private final RoleFixtures roles;

  @Test
  void shouldKeepPublicEndpointsAccessibleWithoutInternalCredentials() throws Exception {
    // When
    val response = api.request(port, HttpMethod.GET.name(), "/api/app-config", null, null);

    // Then
    assertEquals(200, response.statusCode());
    assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
    assertEquals(
        "camera=(), microphone=(), geolocation=()",
        response.headers().firstValue(HttpHeaderNames.PERMISSIONS_POLICY).orElseThrow());
  }

  @Test
  void shouldResolveAnActiveNativeUserWithoutABearerToken() throws Exception {
    // Given
    val userId = identities.nativeUser(UserStatus.ACTIVE);

    // When
    val response = profile(INTERNAL_KEY, userId.toString());

    // Then
    assertEquals(200, response.statusCode());
    assertEquals(userId.toString(), identityApi.user(response).id());
    assertEquals(identities.email(userId), identityApi.user(response).email());
  }

  @Test
  void shouldResolveOidcUsersFromTheirStoredPrincipalIdAndCurrentGrants() throws Exception {
    // Given
    val principal =
        OidcPrincipalIdEncoder.userId(
            "https://identity.example.test", UUID.randomUUID().toString());
    roles.globalAdministrator(principal);

    // When
    val response = profile(INTERNAL_KEY, principal);

    // Then
    assertEquals(200, response.statusCode());
    val user = identityApi.user(response);
    assertEquals(principal, user.id());
    assertTrue(user.permissions().contains("course:manage"));
  }

  @Test
  void shouldNotInventOidcRolesWhenTheUserHasNoGrants() throws Exception {
    // Given
    val principal =
        OidcPrincipalIdEncoder.userId(
            "https://identity.example.test", UUID.randomUUID().toString());

    // When
    val response = profile(INTERNAL_KEY, principal);

    // Then
    assertEquals(200, response.statusCode());
    assertTrue(identityApi.user(response).permissions().isEmpty());
  }

  @ParameterizedTest
  @EnumSource(
      value = UserStatus.class,
      names = {"DISABLED", "PENDING_VERIFICATION"})
  void shouldRejectNativeUsersWhoCannotSignIn(final UserStatus status) throws Exception {
    // Given
    val user = identities.nativeUser(status);

    // When
    val response = profile(INTERNAL_KEY, user.toString());

    // Then
    assertEquals(401, response.statusCode());
  }

  @Test
  void shouldRejectAnIncorrectKeyWithoutDisclosingTheConfiguredSecret() throws Exception {
    // When
    val response = profile("incorrect-key", UUID.randomUUID().toString());

    // Then
    assertEquals(401, response.statusCode());
    assertFalse(response.body().contains(INTERNAL_KEY));
  }

  @Test
  void shouldRejectUserHeadersWithoutAServiceCredential() throws Exception {
    // Given
    val headers = Map.of(HttpHeaderNames.USER_ID, UUID.randomUUID().toString());

    // When
    val response = api.request(port, HttpMethod.GET.name(), "/api/auth/me", null, null, headers);

    // Then
    assertEquals(401, response.statusCode());
  }

  @Test
  void shouldRejectUnknownNativeUsers() throws Exception {
    // When
    val response = profile(INTERNAL_KEY, UUID.randomUUID().toString());

    // Then
    assertEquals(401, response.statusCode());
  }

  private HttpResponse<String> profile(final String key, final String userId) throws Exception {
    val headers = Map.of(HttpHeaderNames.INTERNAL_KEY, key, HttpHeaderNames.USER_ID, userId);
    return api.request(port, HttpMethod.GET.name(), "/api/auth/me", null, null, headers);
  }
}
