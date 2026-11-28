package io.trellis.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.trellis.common.id.EntityIds;
import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EntityIdsTest {
  @ParameterizedTest
  @ValueSource(strings = {"1", "9007199254740993", "9223372036854775807"})
  void shouldPreserveCanonicalPositiveLongIds(final String value) {
    // When
    val id = EntityIds.parse(value);

    // Then
    assertEquals(value, id.toString());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "0",
        "-1",
        "+1",
        "01",
        " 1",
        "1 ",
        "1.0",
        "1e2",
        "9223372036854775808",
        "0193a5c0-0000-7000-8000-000000000001",
        "oidc-user:external"
      })
  void shouldRejectNonCanonicalEntityIds(final String value) {
    // When & Then
    assertThrows(IllegalArgumentException.class, () -> EntityIds.parse(value));
  }
}
