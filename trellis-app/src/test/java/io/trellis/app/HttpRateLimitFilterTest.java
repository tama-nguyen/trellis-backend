package io.trellis.app;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.trellis.common.error.RateLimitedException;
import io.trellis.common.error.ServiceUnavailableException;
import io.trellis.platform.aspect.ratelimit.RateLimitEngine;
import io.trellis.platform.web.error.ProblemResponseWriter;
import io.trellis.platform.web.security.ClientAddressResolver;
import io.trellis.platform.web.security.HttpRateLimitFilter;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.List;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class HttpRateLimitFilterTest {
  private final RateLimitEngine engine = mock(RateLimitEngine.class);
  private final ProblemResponseWriter problems = mock(ProblemResponseWriter.class);
  private final HttpRateLimitFilter filter =
      new HttpRateLimitFilter(engine, new ClientAddressResolver(""), problems);

  @Test
  void allSevenRoutesConsumeBeforeController() throws Exception {
    when(engine.consume(anyString(), anyString())).thenReturn(Duration.ZERO);
    for (val path :
        List.of(
            "login",
            "register",
            "forgot-password",
            "reset-password",
            "verify-email",
            "refresh",
            "logout")) {
      clearInvocations(engine);
      val request = request("POST", path);
      val chain = mock(FilterChain.class);
      filter.doFilter(request, new MockHttpServletResponse(), chain);
      val order = inOrder(engine, chain);
      order.verify(engine).consume(anyString(), eq("192.0.2.1"));
      order.verify(chain).doFilter(eq(request), any());
    }
  }

  @Test
  void quotaExceededNeverRunsController() throws Exception {
    when(engine.consume(anyString(), anyString())).thenReturn(Duration.ofSeconds(2));
    val chain = mock(FilterChain.class);
    filter.doFilter(request("POST", "login"), new MockHttpServletResponse(), chain);
    verify(problems).write(any(), any(), isA(RateLimitedException.class));
    verifyNoInteractions(chain);
  }

  @Test
  void outageStopsNativeRouteAndLeavesMeAndPreflightAvailable() throws Exception {
    when(engine.consume(anyString(), anyString()))
        .thenThrow(new ServiceUnavailableException("offline", null, null));
    val chain = mock(FilterChain.class);
    filter.doFilter(request("POST", "login"), new MockHttpServletResponse(), chain);
    verifyNoInteractions(chain);
    verify(problems).write(any(), any(), isA(ServiceUnavailableException.class));
    filter.doFilter(request("GET", "me"), new MockHttpServletResponse(), chain);
    filter.doFilter(request("OPTIONS", "login"), new MockHttpServletResponse(), chain);
    verify(chain, times(2)).doFilter(any(), any());
    verify(engine).consume(anyString(), anyString());
  }

  private MockHttpServletRequest request(final String method, final String path) {
    val request = new MockHttpServletRequest(method, "/api/auth/" + path);
    request.setServletPath("/api/auth/" + path);
    request.setRemoteAddr("192.0.2.1");
    return request;
  }
}
