package io.trellis.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.trellis.common.error.*;
import io.trellis.platform.aspect.ratelimit.RateLimitEngine;
import io.trellis.platform.web.error.ProblemResponseWriter;
import io.trellis.platform.web.security.*;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class HttpRateLimitFilterTest {
  private final RateLimitEngine engine = mock(RateLimitEngine.class);
  private final ProblemResponseWriter problems = mock(ProblemResponseWriter.class);
  private final HttpRateLimitFilter filter =
      new HttpRateLimitFilter(engine, new ClientAddressResolver(""), problems);

  @Test
  void allSevenRoutesConsumeBeforeController() throws Exception {
    when(engine.consume(anyString(), anyString())).thenReturn(Duration.ZERO);
    for (String path :
        List.of(
            "login",
            "register",
            "forgot-password",
            "reset-password",
            "verify-email",
            "refresh",
            "logout")) {
      clearInvocations(engine);
      var request = request("POST", path);
      var chain = mock(FilterChain.class);
      filter.doFilter(request, new MockHttpServletResponse(), chain);
      var order = inOrder(engine, chain);
      order.verify(engine).consume(anyString(), eq("192.0.2.1"));
      order.verify(chain).doFilter(eq(request), any());
    }
  }

  @Test
  void quotaExceededNeverRunsController() throws Exception {
    when(engine.consume(anyString(), anyString())).thenReturn(Duration.ofSeconds(2));
    var chain = mock(FilterChain.class);
    filter.doFilter(request("POST", "login"), new MockHttpServletResponse(), chain);
    verify(problems).write(any(), any(), isA(RateLimitedException.class));
    verifyNoInteractions(chain);
  }

  @Test
  void outageStopsNativeRouteAndLeavesMeAndPreflightAvailable() throws Exception {
    when(engine.consume(anyString(), anyString()))
        .thenThrow(new ServiceUnavailableException("offline", null, null));
    var chain = mock(FilterChain.class);
    filter.doFilter(request("POST", "login"), new MockHttpServletResponse(), chain);
    verifyNoInteractions(chain);
    verify(problems).write(any(), any(), isA(ServiceUnavailableException.class));
    filter.doFilter(request("GET", "me"), new MockHttpServletResponse(), chain);
    filter.doFilter(request("OPTIONS", "login"), new MockHttpServletResponse(), chain);
    verify(chain, times(2)).doFilter(any(), any());
    verify(engine).consume(anyString(), anyString());
  }

  private MockHttpServletRequest request(String method, String path) {
    var request = new MockHttpServletRequest(method, "/api/auth/" + path);
    request.setServletPath("/api/auth/" + path);
    request.setRemoteAddr("192.0.2.1");
    return request;
  }
}
