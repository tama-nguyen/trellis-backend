package io.trellis.platform.web.error;

import io.trellis.common.error.ProblemFields;
import io.trellis.common.error.ProblemTypes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes a problem straight to the servlet response, for code that runs before Spring MVC (security
 * entry point, access-denied handler, filters). Same body as {@link GlobalExceptionHandler}.
 */
public final class ProblemResponseWriter {
  private final ProblemFactory problems;
  private final JsonMapper json;

  public ProblemResponseWriter(ProblemFactory problems, JsonMapper json) {
    this.problems = problems;
    this.json = json;
  }

  public void write(HttpServletRequest request, HttpServletResponse response, Throwable error)
      throws IOException {
    MappedProblem mapped = problems.resolve(error);
    ProblemDetail detail = problems.create(mapped, request, error);
    response.setStatus(detail.getStatus());
    mapped
        .headers()
        .forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
    response.setContentType(ProblemTypes.MEDIA_TYPE);
    json.writeValue(response.getOutputStream(), toMap(detail));
  }

  private static Map<String, Object> toMap(ProblemDetail detail) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put(ProblemFields.TYPE, String.valueOf(detail.getType()));
    body.put(ProblemFields.TITLE, detail.getTitle());
    body.put(ProblemFields.STATUS, detail.getStatus());
    if (detail.getDetail() != null) {
      body.put(ProblemFields.DETAIL, detail.getDetail());
    }
    if (detail.getInstance() != null) {
      body.put(ProblemFields.INSTANCE, String.valueOf(detail.getInstance()));
    }
    if (detail.getProperties() != null) {
      body.putAll(detail.getProperties());
    }
    return body;
  }
}
