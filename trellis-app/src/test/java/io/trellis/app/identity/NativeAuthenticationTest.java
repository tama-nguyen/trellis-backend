package io.trellis.app.identity;

import static io.trellis.app.support.IdentityFixtures.PASSWORD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.trellis.app.support.IdentityApi;
import io.trellis.app.support.IdentityFixtures;
import io.trellis.app.support.MailWorkerFixture;
import io.trellis.app.support.SecurityTestContext;
import io.trellis.identity.application.port.IdentityMailDelivery;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.view.AuthTokens;
import io.trellis.identity.domain.user.UserStatus;
import io.trellis.platform.audit.AuditEventFilter;
import io.trellis.platform.audit.AuditEventStore;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

@SecurityTestContext
@RequiredArgsConstructor
class NativeAuthenticationTest {
  private static final String REPLACEMENT_PASSWORD = "Another-valid-password-42!";

  @LocalServerPort private int port;

  private final UserRepository users;
  private final IdentityMailDelivery delivery;
  private final IdentityFixtures fixtures;
  private final IdentityApi api;
  private final MailWorkerFixture workers;
  private final AuditEventStore auditEvents;

  @Test
  void nativeSessionReturnsPublicProfile() throws Exception {
    // Given
    val session = fixtures.session(UserStatus.ACTIVE);

    // When
    val response = api.profile(port, session.access().raw());

    // Then
    assertEquals(HttpStatus.OK.value(), response.statusCode());
    assertEquals(session.userId().toString(), api.user(response).id());
    assertFalse(api.document(response).has("password"));
    assertFalse(api.document(response).has("refreshToken"));
  }

  @Test
  void refreshInvalidatesPreviousAccessToken() throws Exception {
    // Given
    val session = fixtures.session(UserStatus.ACTIVE);

    // When
    val response = api.refresh(port, session.refresh().raw());

    // Then
    assertEquals(HttpStatus.OK.value(), response.statusCode());
    val rotated = api.tokens(response);
    assertEquals(
        HttpStatus.UNAUTHORIZED.value(), api.profile(port, session.access().raw()).statusCode());
    assertEquals(HttpStatus.OK.value(), api.profile(port, rotated.accessToken()).statusCode());
  }

  @Test
  void logoutRevokesSession() throws Exception {
    // Given
    val session = fixtures.session(UserStatus.ACTIVE);

    // When
    val response = api.logout(port, session.refresh().raw());

    // Then
    assertEquals(HttpStatus.NO_CONTENT.value(), response.statusCode());
    assertEquals(
        HttpStatus.UNAUTHORIZED.value(), api.profile(port, session.access().raw()).statusCode());
  }

  @Test
  void logoutRevokesRotatedSession() throws Exception {
    // Given
    val session = fixtures.session(UserStatus.ACTIVE);
    val refresh = api.refresh(port, session.refresh().raw());
    assertEquals(
        HttpStatus.OK.value(), refresh.statusCode(), "fixture refresh must rotate the session");
    val rotated = api.tokens(refresh);

    // When
    val response = api.logout(port, rotated.refreshToken());

    // Then
    assertEquals(HttpStatus.NO_CONTENT.value(), response.statusCode());
    assertEquals(
        HttpStatus.UNAUTHORIZED.value(), api.profile(port, rotated.accessToken()).statusCode());
  }

  @ParameterizedTest(name = "Native access for a {0} user")
  @EnumSource(UserStatus.class)
  void sessionRequiresActiveUser(final UserStatus status) throws Exception {
    // Given
    val session = fixtures.session(status);
    val expected = status == UserStatus.ACTIVE ? HttpStatus.OK : HttpStatus.UNAUTHORIZED;

    // When
    val response = api.profile(port, session.access().raw());

    // Then
    assertEquals(expected.value(), response.statusCode());
  }

  @Test
  void accessTokenExpiresAtBoundary() {
    // Given
    val session = fixtures.session(UserStatus.ACTIVE);

    // When
    val user = users.userByActiveAccessToken(session.access().hash(), session.accessExpiry());

    // Then
    assertTrue(user.isEmpty());
  }

  @Test
  void revokedSessionCannotAuthenticate() throws Exception {
    // Given
    val session = fixtures.session(UserStatus.ACTIVE);
    fixtures.revoke(session);

    // When
    val response = api.profile(port, session.access().raw());

    // Then
    assertEquals(HttpStatus.UNAUTHORIZED.value(), response.statusCode());
  }

  @Test
  void passwordResetChangesPasswordAndRevokesAllSessions() throws Exception {
    // Given
    val user = fixtures.nativeUser(UserStatus.ACTIVE);
    val first = signIn(user);
    val second = signIn(user);
    val code = emailedResetCode(user);

    // When
    val response = api.resetPassword(port, fixtures.email(user), code, REPLACEMENT_PASSWORD);

    // Then
    assertEquals(HttpStatus.NO_CONTENT.value(), response.statusCode());
    assertEquals(
        HttpStatus.UNAUTHORIZED.value(), api.profile(port, first.accessToken()).statusCode());
    assertEquals(
        HttpStatus.UNAUTHORIZED.value(), api.profile(port, second.accessToken()).statusCode());
    assertEquals(
        HttpStatus.UNAUTHORIZED.value(),
        api.login(port, fixtures.email(user), PASSWORD).statusCode());
    assertEquals(
        HttpStatus.OK.value(),
        api.login(port, fixtures.email(user), REPLACEMENT_PASSWORD).statusCode());
    val audit =
        auditEvents.page(
            new AuditEventFilter("Identity", user.toString(), null, null, null), null, 50);
    assertFalse(audit.hasNext());
    assertTrue(
        audit.items().stream().anyMatch(event -> "identity.reset-password".equals(event.action())));
  }

  @Test
  void activeUserCanSignIn() throws Exception {
    // Given
    val user = fixtures.nativeUser(UserStatus.ACTIVE);

    // When
    val response = api.login(port, fixtures.email(user), PASSWORD);

    // Then
    assertEquals(HttpStatus.OK.value(), response.statusCode());
    assertEquals(
        HttpStatus.OK.value(), api.profile(port, api.tokens(response).accessToken()).statusCode());
  }

  private AuthTokens signIn(final Long user) throws Exception {
    val response = api.login(port, fixtures.email(user), PASSWORD);
    assertEquals(HttpStatus.OK.value(), response.statusCode(), "session fixture must sign in");
    return api.tokens(response);
  }

  private String emailedResetCode(final Long user) throws Exception {
    val response = api.forgotPassword(port, fixtures.email(user));
    assertEquals(
        HttpStatus.ACCEPTED.value(), response.statusCode(), "reset fixture must enqueue mail");
    workers.worker().tick();
    val code = ArgumentCaptor.forClass(String.class);
    verify(delivery).sendPasswordResetCode(eq(fixtures.email(user)), anyString(), code.capture());
    return code.getValue();
  }
}
