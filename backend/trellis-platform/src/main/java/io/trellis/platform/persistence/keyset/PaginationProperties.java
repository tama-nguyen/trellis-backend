package io.trellis.platform.persistence.keyset;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param cursorSecret HMAC key (at least 16 bytes) that signs page cursors so clients cannot forge them; when
 *     empty, cursors are unsigned (acceptable only for local development)
 */
@ConfigurationProperties(prefix = "trellis.pagination")
public record PaginationProperties(String cursorSecret) {}
