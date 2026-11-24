package io.trellis.common.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * RFC 9562 UUID version 7: 48-bit Unix milliseconds, then random bits. Time-ordered, so B-tree
 * inserts stay append-mostly and the id doubles as the keyset tie-breaker.
 *
 * <p>Ordering is only guaranteed across milliseconds; two ids from the same millisecond are in
 * random order. That is enough for indexes and tie-breaking; never use it as a sequence number.
 */
public final class UuidV7 {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Clock SYSTEM = Clock.systemUTC();
  private static final long VERSION_7 = 0x7000L;
  private static final long VARIANT_RFC = 0x8000_0000_0000_0000L;
  private static final long RAND_A_MASK = 0x0FFFL;
  private static final long RAND_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL;
  private static final int TIMESTAMP_SHIFT = 16;
  private static final long TIMESTAMP_MASK = 0xFFFF_FFFF_FFFFL;

  private UuidV7() {}

  /** New id from the system UTC clock. */
  public static UUID next() {
    return next(SYSTEM);
  }

  /** New id stamped with {@code clock}'s current millisecond; tests pass a fixed clock. */
  public static UUID next(Clock clock) {
    long millis = clock.millis() & TIMESTAMP_MASK;
    long msb = (millis << TIMESTAMP_SHIFT) | VERSION_7 | (RANDOM.nextLong() & RAND_A_MASK);
    long lsb = VARIANT_RFC | (RANDOM.nextLong() & RAND_B_MASK);
    return new UUID(msb, lsb);
  }

  /** The creation time embedded in a v7 id. */
  public static Instant timestampOf(UUID id) {
    if (id.version() != 7) {
      throw new IllegalArgumentException("Not a UUIDv7: " + id);
    }
    return Instant.ofEpochMilli(id.getMostSignificantBits() >>> TIMESTAMP_SHIFT);
  }
}
