package io.trellis.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.trellis.common.context.Actor;
import io.trellis.common.context.ActorType;
import io.trellis.common.context.IdentityOrigin;
import io.trellis.common.security.Permission;
import io.trellis.learningcontent.api.security.CoursePermissions;
import io.trellis.platform.security.SecurityProperties;
import io.trellis.platform.security.jwt.JwtActorMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

class JwtActorMapperTest {
  private static final String ISSUER = "https://issuer.example.test";
  private static final String SUBJECT = "same-id";
  private static final String READER_ROLE = "learner";
  private JwtActorMapper mapper;

  @BeforeEach
  void configureClaimMapping() {
    val properties = new SecurityProperties();
    ReflectionTestUtils.setField(properties, "rolesClaim", "realm_access.roles");
    ReflectionTestUtils.setField(properties, "permissionsClaim", "permissions");
    ReflectionTestUtils.setField(properties, "serviceClaim", "client_id");
    mapper = new JwtActorMapper(properties, () -> Set.of(Permission.of(CoursePermissions.READ)));
  }

  @Test
  void shouldMapOidcUserWithOnlyDeclaredPermissions() {
    // Given
    val token = userToken();

    // When
    val actor = mapper.toActor(token);

    // Then
    val user = assertInstanceOf(Actor.UserActor.class, actor);
    assertEquals(ActorType.USER, user.type());
    assertEquals(IdentityOrigin.OIDC, user.origin());
    assertEquals(Set.of(READER_ROLE), user.roles());
    assertEquals(Set.of(Permission.of(CoursePermissions.READ)), user.permissions());
  }

  @Test
  void shouldMapClientClaimToServiceActor() {
    // Given
    val token = serviceToken();

    // When
    val actor = mapper.toActor(token);

    // Then
    assertEquals(ActorType.SERVICE, actor.type());
  }

  @Test
  void shouldPartitionUserAndServiceIdsForTheSameSubject() {
    // Given
    val user = userToken();
    val service = serviceToken();

    // When
    val userActor = mapper.toActor(user);
    val serviceActor = mapper.toActor(service);

    // Then
    assertNotEquals(userActor.id(), serviceActor.id());
  }

  @Test
  void shouldRejectUserWithoutSubject() {
    // Given
    val token = token().claim("iss", ISSUER).build();

    // When & Then
    assertThrows(BadCredentialsException.class, () -> mapper.toActor(token));
  }

  @Test
  void shouldRejectUserWithoutIssuer() {
    // Given
    val token = token().subject(SUBJECT).build();

    // When & Then
    assertThrows(BadCredentialsException.class, () -> mapper.toActor(token));
  }

  private Jwt userToken() {
    return token()
        .claim("iss", ISSUER)
        .subject(SUBJECT)
        .claim("realm_access", Map.of("roles", List.of(READER_ROLE)))
        .claim("permissions", List.of(CoursePermissions.READ, "course:invented"))
        .build();
  }

  private Jwt serviceToken() {
    return token().claim("iss", ISSUER).claim("client_id", SUBJECT).build();
  }

  private Jwt.Builder token() {
    return Jwt.withTokenValue("fixture").header("alg", "RS256");
  }
}
