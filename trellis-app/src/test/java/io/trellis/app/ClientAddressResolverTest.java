package io.trellis.app;

import static org.junit.jupiter.api.Assertions.*;

import io.trellis.platform.web.security.ClientAddressResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressResolverTest {
  @Test
  void ignoresForwardedAddressFromUntrustedPeer() {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("192.0.2.1");
    request.addHeader("X-Forwarded-For", "198.51.100.1");
    assertEquals("192.0.2.1", new ClientAddressResolver("10.0.0.0/8").resolve(request));
  }

  @Test
  void walksTrustedChainFromRightAndStopsAtFirstUntrustedHop() {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("10.0.0.1");
    request.addHeader("X-Forwarded-For", "198.51.100.9,192.0.2.1,10.0.0.2");
    assertEquals("192.0.2.1", new ClientAddressResolver("10.0.0.0/8").resolve(request));
  }

  @Test
  void equivalentIpv6AddressesCanonicalize() {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("2001:db8::1");
    var first = new ClientAddressResolver("").resolve(request);
    request.setRemoteAddr("2001:db8:0:0:0:0:0:1");
    assertEquals(first, new ClientAddressResolver("").resolve(request));
  }
}
