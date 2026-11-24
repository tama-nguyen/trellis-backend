package io.trellis.platform.web.error;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryAfterTest {
  @Test
  void roundsFractionalSecondsUpIncludingSubMillisecondPart() {
    assertEquals("2", TrellisProblemMapper.retryAfterSeconds(Duration.ofSeconds(1).plusNanos(1)));
    assertEquals("1", TrellisProblemMapper.retryAfterSeconds(Duration.ZERO));
    assertEquals("2", TrellisProblemMapper.retryAfterSeconds(Duration.ofSeconds(2)));
  }
}
