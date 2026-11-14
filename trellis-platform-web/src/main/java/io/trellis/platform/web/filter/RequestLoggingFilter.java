package io.trellis.platform.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One structured line per request: method, path, status, duration. The query string and body are never
 * logged, because they routinely carry personal data and tokens.
 */
public final class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger("trellis.http");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            log.atInfo()
                    .addKeyValue("http_method", request.getMethod())
                    .addKeyValue("http_path", request.getRequestURI())
                    .addKeyValue("http_status", response.getStatus())
                    .addKeyValue("duration_ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
                    .log("HTTP request completed");
        }
    }
}
