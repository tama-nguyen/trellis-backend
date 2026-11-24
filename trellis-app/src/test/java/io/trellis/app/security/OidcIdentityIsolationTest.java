package io.trellis.app.security;

import static io.trellis.app.support.TestJwtIssuer.ISSUER;
import static io.trellis.app.support.TestJwtIssuer.jwt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.trellis.app.support.IdentityApi;
import io.trellis.app.support.IdentityFixtures;
import io.trellis.app.support.RoleFixtures;
import io.trellis.app.support.SecurityTestContext;
import io.trellis.identity.domain.user.UserStatus;
import io.trellis.learningcontent.api.security.CoursePermissions;
import io.trellis.platform.security.jwt.OidcPrincipalIdEncoder;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

@SecurityTestContext
@RequiredArgsConstructor
class OidcIdentityIsolationTest {
  @LocalServerPort private int port;
  private final IdentityFixtures identities;
  private final RoleFixtures roles;
  private final IdentityApi api;

  @Test
  void oidcSubjectCannotInheritNativeIdentityOrGrants() throws Exception {
    // Given
    val nativeUser = identities.nativeUser(UserStatus.ACTIVE);
    roles.globalAdministrator(nativeUser.toString());
    val external = jwt(nativeUser.toString(), RoleFixtures.READER_ROLE);

    // When
    val response = api.profile(port, external);

    // Then
    assertEquals(HttpStatus.OK.value(), response.statusCode());
    val profile = api.user(response);
    assertNotEquals(nativeUser.toString(), profile.id());
    assertEquals(OidcPrincipalIdEncoder.userId(ISSUER, nativeUser.toString()), profile.id());
    assertEquals("external@example.test", profile.email());
    assertFalse(profile.permissions().contains(CoursePermissions.MANAGE));
  }
}
