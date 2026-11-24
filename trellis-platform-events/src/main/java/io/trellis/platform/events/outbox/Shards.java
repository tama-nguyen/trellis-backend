package io.trellis.platform.events.outbox;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Shard of a partition key: {@code crc32(utf8(key)) mod shards}. Same routing as DataHub's {@code
 * MetadataQueueRouting.stablePartitionId}; any other writer (Python) must use the same formula.
 */
public final class Shards {
  private Shards() {}

  public static int of(String partitionKey, int shards) {
    CRC32 crc = new CRC32();
    crc.update(partitionKey.getBytes(StandardCharsets.UTF_8));
    return (int) (crc.getValue() % shards);
  }
}
