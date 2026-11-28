package io.trellis.platform.web.security;

import io.trellis.common.http.HttpHeaderNames;
import io.trellis.platform.security.ActorBearerAuthenticationToken;
import io.trellis.platform.security.DelegatedUserResolver;
import io.trellis.platform.security.SecurityProperties;
import io.trellis.platform.web.error.ProblemResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates trusted internal callers using a service key and a delegated backend principal ID.
 */
@RequiredArgsConstructor
final class InternalKeyFilter extends OncePerRequestFilter {

  private static final String API_PREFIX = "/api/";
  private static final String INVALID_CREDENTIALS = "Invalid internal authentication";

  private final SecurityProperties securityProperties;
  private final ObjectProvider<DelegatedUserResolver> delegatedUserResolverProvider;
  private final ProblemResponseWriter problemResponseWriter;

  @Override
  protected boolean shouldNotFilter(final HttpServletRequest request) {
    return !request.getServletPath().startsWith(API_PREFIX)
        || (request.getHeader(HttpHeaderNames.INTERNAL_KEY) == null
            && request.getHeader(HttpHeaderNames.USER_ID) == null);
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    try {
      final String expectedInternalKey = securityProperties.internalKey();

      final List<String> internalKeys =
          Collections.list(request.getHeaders(HttpHeaderNames.INTERNAL_KEY));

      final List<String> delegatedUserIds =
          Collections.list(request.getHeaders(HttpHeaderNames.USER_ID));

      if (expectedInternalKey == null
          || expectedInternalKey.isEmpty()
          || internalKeys.size() != 1
          || delegatedUserIds.size() != 1
          || request.getHeader(HttpHeaders.AUTHORIZATION) != null
          || !MessageDigest.isEqual(
              expectedInternalKey.getBytes(StandardCharsets.UTF_8),
              internalKeys.getFirst().getBytes(StandardCharsets.UTF_8))) {
        throw new BadCredentialsException(INVALID_CREDENTIALS);
      }

      final DelegatedUserResolver delegatedUserResolver =
          delegatedUserResolverProvider.getIfAvailable();

      if (delegatedUserResolver == null) {
        throw new BadCredentialsException(INVALID_CREDENTIALS);
      }

      final SecurityContext securityContext = SecurityContextHolder.createEmptyContext();

      securityContext.setAuthentication(
          new ActorBearerAuthenticationToken(
              delegatedUserResolver.resolve(delegatedUserIds.getFirst())));

      SecurityContextHolder.setContext(securityContext);
    } catch (AuthenticationException exception) {
      SecurityContextHolder.clearContext();
      problemResponseWriter.write(request, response, exception);
      return;
    }

    filterChain.doFilter(request, response);
  }
}
