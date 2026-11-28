package io.trellis.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.trellis.common.error.PlatformErrorCode;
import io.trellis.common.error.TrellisException;
import io.trellis.common.page.Cursor;
import io.trellis.common.page.CursorCodec;
import io.trellis.platform.persistence.keyset.KeyType;
import io.trellis.platform.persistence.keyset.KeysetQuery;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KeysetCursorValidationTest {
  private static final String CREATED_AT = "2026-11-27T00:00:00Z";
  private static final CursorCodec CODEC =
      CursorCodec.signed("local-cursor-test-secret".getBytes(StandardCharsets.UTF_8));
  private static final KeysetQuery QUERY =
      KeysetQuery.descending(
          KeysetQuery.SortKey.of("created_at", KeyType.TIMESTAMP),
          KeysetQuery.SortKey.of("id", KeyType.LONG));

  @Test
  void shouldRejectCursorWithTheWrongNumberOfKeys() {
    // Given
    val cursor = CODEC.decode(CODEC.encode(new Cursor(List.of(CREATED_AT))));

    // When
    val error = assertThrows(TrellisException.class, () -> QUERY.parameters(cursor, 10));

    // Then
    assertEquals(PlatformErrorCode.INVALID_CURSOR, error.errorCode());
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-a-timestamp", "", "2026-13-01T00:00:00Z"})
  void shouldRejectSignedCursorWithInvalidTimestamp(final String timestamp) {
    // Given
    val cursor = CODEC.decode(CODEC.encode(new Cursor(List.of(timestamp, "1"))));

    // When
    val error = assertThrows(TrellisException.class, () -> QUERY.parameters(cursor, 10));

    // Then
    assertEquals(PlatformErrorCode.INVALID_CURSOR, error.errorCode());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0193a5c0-0000-7000-8000-000000000001", "9223372036854775808", "invalid"})
  void shouldRejectSignedCursorKeysIncompatibleWithLongIds(final String oldKey) {
    // Given
    val token = CODEC.encode(new Cursor(List.of(CREATED_AT, oldKey)));
    val cursor = CODEC.decode(token);

    // When
    val error = assertThrows(TrellisException.class, () -> QUERY.parameters(cursor, 10));

    // Then
    assertEquals(PlatformErrorCode.INVALID_CURSOR, error.errorCode());
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "9007199254740993", "9223372036854775807"})
  void shouldKeepLongCursorKeysLossless(final String key) {
    // Given
    val cursor = CODEC.decode(CODEC.encode(new Cursor(List.of(CREATED_AT, key))));

    // When
    val parameters = QUERY.parameters(cursor, 10);

    // Then
    assertEquals(Long.valueOf(key), parameters.get("keyset_k1"));
    assertEquals(11, parameters.get("keyset_limit"));
  }
}
