package io.trellis.platform.persistence.keyset;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;

/**
 * cursorSecret: HMAC key (at least 16 bytes) that signs page cursors so clients cannot forge them;
 * when empty, cursors are unsigned (acceptable only for local development)
 */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public final class PaginationProperties {
  @Value("${trellis.pagination.cursor-secret:}")
  private String cursorSecret;
}
