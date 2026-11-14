package io.trellis.app.setup;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * S3-compatible object storage (MinIO locally). Read by setup today; a storage module will own it later.
 *
 * @param endpoint e.g. {@code http://minio:9000} ({@code TRELLIS_S3_ENDPOINT})
 * @param region signing region; MinIO accepts {@code us-east-1}
 * @param bucket bucket name ({@code TRELLIS_S3_BUCKET})
 * @param accessKey access key id ({@code TRELLIS_S3_ACCESS_KEY})
 * @param secretKey secret key ({@code TRELLIS_S3_SECRET_KEY}); sensitive
 */
@ConfigurationProperties(prefix = "trellis.storage.s3")
public record S3Properties(
        String endpoint, @DefaultValue("us-east-1") String region, String bucket, String accessKey, String secretKey) {}
