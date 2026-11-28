package io.trellis.app.setup;

import java.time.Duration;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;

/**
 * Setup mode switches and setup-only HTTP settings.
 *
 * <p>The flag is named {@code create-s3-bucket} on purpose: {@code TRELLIS_SETUP_S3_BUCKET} must
 * not accidentally bind to a nested {@code s3.bucket} property.
 *
 * <p>enabled: run setup and exit; {@code TRELLIS_SETUP_ENABLED} createKafkaTopics: create Kafka
 * topics ({@code TRELLIS_SETUP_KAFKA_TOPICS}) topics: event types whose topic and {@code .dlq}
 * topic must exist partitions: partitions of each created topic replicationFactor: replication
 * factor of each created topic createS3Bucket: create the object-storage bucket ({@code
 * TRELLIS_SETUP_S3_BUCKET}) s3: S3 setup HTTP timeouts, configured separately for each environment
 */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
public final class SetupProperties {
  @Value("${trellis.setup.enabled:false}")
  private boolean enabled;

  @Value("${trellis.setup.create-kafka-topics:false}")
  private boolean createKafkaTopics;

  @Value("${trellis.setup.topics:}")
  private List<String> topics;

  @Value("${trellis.setup.partitions:3}")
  private int partitions;

  @Value("${trellis.setup.replication-factor:1}")
  private short replicationFactor;

  @Value("${trellis.setup.create-s3-bucket:false}")
  private boolean createS3Bucket;

  @Value("${trellis.setup.s3.connect-timeout:5s}")
  private Duration s3ConnectTimeout;

  @Value("${trellis.setup.s3.request-timeout:30s}")
  private Duration s3RequestTimeout;

  // Bean conditions compare the text: 1 or yes binds as a boolean but would match neither mode.
  @Value("${trellis.setup.enabled:false}")
  void requireTrueOrFalse(final String value) {
    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
      throw new IllegalStateException("TRELLIS_SETUP_ENABLED must be true or false");
    }
  }

  public SetupProperties(
      boolean enabled,
      boolean createKafkaTopics,
      List<String> topics,
      int partitions,
      short replicationFactor,
      boolean createS3Bucket,
      S3ClientSettings s3) {
    this.enabled = enabled;
    this.createKafkaTopics = createKafkaTopics;
    this.topics = topics;
    this.partitions = partitions;
    this.replicationFactor = replicationFactor;
    this.createS3Bucket = createS3Bucket;
    this.s3ConnectTimeout = s3.connectTimeout();
    this.s3RequestTimeout = s3.requestTimeout();
  }

  public S3ClientSettings s3() {
    return new S3ClientSettings(s3ConnectTimeout, s3RequestTimeout);
  }

  /** Only HTTP client settings; bucket name and credentials belong to {@link S3Properties}. */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class S3ClientSettings {
    private final Duration connectTimeout;
    private final Duration requestTimeout;
  }
}
