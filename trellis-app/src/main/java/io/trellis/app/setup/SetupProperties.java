package io.trellis.app.setup;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Setup mode switches and setup-only HTTP settings.
 *
 * <p>The flag is named {@code create-s3-bucket} on purpose: {@code TRELLIS_SETUP_S3_BUCKET}
 * must not accidentally bind to a nested {@code s3.bucket} property.
 *
 * @param enabled run setup and exit; set by the {@code setup} profile
 * @param createKafkaTopics create Kafka topics ({@code TRELLIS_SETUP_KAFKA_TOPICS})
 * @param topics event types whose topic and {@code .dlq} topic must exist
 * @param partitions partitions of each created topic
 * @param replicationFactor replication factor of each created topic
 * @param createS3Bucket create the object-storage bucket ({@code TRELLIS_SETUP_S3_BUCKET})
 * @param s3 S3 setup HTTP timeouts, configured separately for each environment
 */
@ConfigurationProperties(prefix = "trellis.setup")
public record SetupProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("false") boolean createKafkaTopics,
        @DefaultValue List<String> topics,
        @DefaultValue("3") int partitions,
        @DefaultValue("1") short replicationFactor,
        @DefaultValue("false") boolean createS3Bucket,
        @DefaultValue S3ClientSettings s3) {

    /** Only HTTP client settings; bucket name and credentials belong to {@link S3Properties}. */
    public record S3ClientSettings(
            @DefaultValue("5s") Duration connectTimeout,
            @DefaultValue("30s") Duration requestTimeout) {}
}
