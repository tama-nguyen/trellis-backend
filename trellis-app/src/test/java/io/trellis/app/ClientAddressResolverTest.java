package io.trellis.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.trellis.platform.web.security.ClientAddressResolver;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressResolverTest {
  @Test
  void ignoresForwardedAddressFromUntrustedPeer() {
    // Given
    val request = new MockHttpServletRequest();
    request.setRemoteAddr("192.0.2.1");
    request.addHeader("X-Forwarded-For", "198.51.100.1");
    val resolver = new ClientAddressResolver("10.0.0.0/8");

    // When
    val address = resolver.resolve(request);

    // Then
    assertEquals("192.0.2.1", address);
  }

  @Test
  void walksTrustedChainFromRightAndStopsAtFirstUntrustedHop() {
    // Given
    val request = new MockHttpServletRequest();
    request.setRemoteAddr("10.0.0.1");
    request.addHeader("X-Forwarded-For", "198.51.100.9,192.0.2.1,10.0.0.2");
    val resolver = new ClientAddressResolver("10.0.0.0/8");

    // When
    val address = resolver.resolve(request);

    // Then
    assertEquals("192.0.2.1", address);
  }

  @Test
  void equivalentIpv6AddressesCanonicalize() {
    // Given
    val request = new MockHttpServletRequest();
    request.setRemoteAddr("2001:db8::1");
    val first = new ClientAddressResolver("").resolve(request);
    request.setRemoteAddr("2001:db8:0:0:0:0:0:1");

    // When
    val second = new ClientAddressResolver("").resolve(request);

    // Then
    assertEquals(first, second);
  }
}
