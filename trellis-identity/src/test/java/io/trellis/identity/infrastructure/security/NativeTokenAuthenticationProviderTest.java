package io.trellis.identity.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.trellis.identity.IdentityProperties;
import io.trellis.identity.application.port.TokenCodec;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.domain.token.TokenType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

class NativeTokenAuthenticationProviderTest {
  @Test
  void shouldRejectInvalidNativeSessionWithoutCallingTheOidcProvider() {
    // Given
    val clock = Clock.fixed(Instant.EPOCH, java.time.ZoneOffset.UTC);
    val tokens = mock(TokenCodec.class);
    val users = mock(UserRepository.class);
    val oidc = mock(AuthenticationProvider.class);
    val nativeProvider =
        new NativeTokenAuthenticationProvider(users, tokens, new IdentityProperties(), clock);
    val manager = new ProviderManager(List.of(nativeProvider, oidc));
    val token = new BearerTokenAuthenticationToken("native-access");
    when(tokens.isType(token.getToken(), TokenType.ACCESS)).thenReturn(true);
    when(tokens.hash(token.getToken())).thenReturn("hashed-access");
    when(users.userByActiveAccessToken("hashed-access", clock.instant()))
        .thenReturn(Optional.empty());
    when(oidc.supports(BearerTokenAuthenticationToken.class)).thenReturn(true);

    // When
    assertThrows(AccountStatusException.class, () -> manager.authenticate(token));

    // Then
    verify(oidc, never()).authenticate(any());
  }
}
