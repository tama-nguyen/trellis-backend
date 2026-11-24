package io.trellis.platform.web.context;

import io.trellis.common.context.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets a controller declare {@code RequestContext ctx} and pass it straight to the application
 * service.
 */
public final class RequestContextArgumentResolver implements HandlerMethodArgumentResolver {
  private final RequestContextFactory factory;

  public RequestContextArgumentResolver(RequestContextFactory factory) {
    this.factory = factory;
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return RequestContext.class.equals(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    return factory.from(webRequest.getNativeRequest(HttpServletRequest.class));
  }
}
