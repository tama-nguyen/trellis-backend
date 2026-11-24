package io.trellis.platform.web.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Matches requests routed to handlers annotated {@link PublicEndpoint}, plus configured public
 * paths. Built lazily on the first request because handler mappings are only complete once the
 * context has refreshed.
 */
final class PublicEndpointRequestMatcher implements RequestMatcher {
  private final RequestMappingHandlerMapping handlerMapping;
  private final List<String> extraPaths;
  private volatile RequestMatcher delegate;

  PublicEndpointRequestMatcher(
      RequestMappingHandlerMapping handlerMapping, List<String> extraPaths) {
    this.handlerMapping = handlerMapping;
    this.extraPaths = List.copyOf(extraPaths);
  }

  @Override
  public boolean matches(HttpServletRequest request) {
    RequestMatcher current = delegate;
    if (current == null) {
      current = build();
      delegate = current;
    }
    return current.matches(request);
  }

  private RequestMatcher build() {
    List<RequestMatcher> matchers = new ArrayList<>();
    extraPaths.forEach(
        path -> matchers.add(PathPatternRequestMatcher.withDefaults().matcher(path)));
    handlerMapping
        .getHandlerMethods()
        .forEach(
            (info, method) -> {
              if (isPublic(method)) {
                addMatchers(info, matchers);
              }
            });
    return matchers.isEmpty() ? request -> false : new OrRequestMatcher(matchers);
  }

  private static boolean isPublic(HandlerMethod method) {
    return AnnotatedElementUtils.hasAnnotation(method.getMethod(), PublicEndpoint.class)
        || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), PublicEndpoint.class);
  }

  private static void addMatchers(RequestMappingInfo info, List<RequestMatcher> matchers) {
    Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
    for (String pattern : info.getPatternValues()) {
      if (methods.isEmpty()) {
        matchers.add(PathPatternRequestMatcher.withDefaults().matcher(pattern));
      }
      for (RequestMethod method : methods) {
        matchers.add(
            PathPatternRequestMatcher.withDefaults()
                .matcher(HttpMethod.valueOf(method.name()), pattern));
      }
    }
  }
}
