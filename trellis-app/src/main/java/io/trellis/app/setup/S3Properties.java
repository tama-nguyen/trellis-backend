package io.trellis.app.setup;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;

/**
 * S3-compatible object storage (MinIO locally). Read by setup today; a storage module will own it
 * later.
 *
 * <p>endpoint: e.g. {@code http://minio:9000} ({@code TRELLIS_S3_ENDPOINT}) region: signing region;
 * MinIO accepts {@code us-east-1} bucket: bucket name ({@code TRELLIS_S3_BUCKET}) accessKey: access
 * key id ({@code TRELLIS_S3_ACCESS_KEY}) secretKey: secret key ({@code TRELLIS_S3_SECRET_KEY});
 * sensitive
 */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public final class S3Properties {
  @Value("${trellis.storage.s3.endpoint:}")
  private String endpoint;

  @Value("${trellis.storage.s3.region:us-east-1}")
  private String region;

  @Value("${trellis.storage.s3.bucket:}")
  private String bucket;

  @Value("${trellis.storage.s3.access-key:}")
  private String accessKey;

  @Value("${trellis.storage.s3.secret-key:}")
  private String secretKey;
}
