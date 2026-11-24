package io.trellis.app.setup;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import lombok.val;

/**
 * Creates a bucket using a path-style, SigV4-signed CreateBucket request.
 *
 * <p>This small setup-only client targets MinIO/S3-compatible endpoints. It intentionally does not
 * implement the complete AWS CreateBucket API (e.g. LocationConstraint or temporary credentials).
 */
final class S3BucketCreator {
  private static final int HTTP_CONFLICT = 409;
  private static final String ALREADY_OWNED = "BucketAlreadyOwnedByYou";
  private static final Pattern BUCKET_NAME = Pattern.compile("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]");

  private final HttpClient http;
  private final Clock clock;
  private final Duration requestTimeout;

  /** Production constructor: timeouts come from the active environment's YAML. */
  S3BucketCreator(final Clock clock, final SetupProperties.S3ClientSettings properties) {
    this(
        HttpClient.newBuilder()
            .connectTimeout(
                positiveTimeout(
                    Objects.requireNonNull(properties, "properties").connectTimeout(), "connect"))
            .build(),
        clock,
        properties.requestTimeout());
  }

  /** Test/integration constructor: enables injecting a configured HTTP client. */
  S3BucketCreator(final HttpClient http, final Clock clock, final Duration requestTimeout) {
    this.http = Objects.requireNonNull(http, "http");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.requestTimeout = positiveTimeout(requestTimeout, "request");
  }

  /**
   * @return {@code true} on a successful 2xx response; {@code false} when the server responds with
   *     409 / BucketAlreadyOwnedByYou
   * @throws IOException on HTTP errors or network failures
   * @throws InterruptedException if the calling thread is interrupted while waiting
   */
  boolean createBucket(final S3Properties s3) throws IOException, InterruptedException {
    Objects.requireNonNull(s3, "s3");
    // The signer needs these three; a blank one yields a request MinIO rejects with an
    // opaque 403, so fail here with the name of the missing setting instead.
    requireNonBlank(s3.region(), "region");
    requireNonBlank(s3.accessKey(), "access key");
    requireNonBlank(s3.secretKey(), "secret key");
    val uri = bucketUri(s3);
    val signedHeaders = AwsSigV4Signer.signEmptyPut(uri, s3, clock.instant());
    val request =
        HttpRequest.newBuilder(uri)
            .timeout(requestTimeout)
            .PUT(HttpRequest.BodyPublishers.noBody())
            .header(AwsSigV4Signer.HEADER_CONTENT_SHA256, signedHeaders.payloadHash())
            .header(AwsSigV4Signer.HEADER_DATE, signedHeaders.amzDate())
            .header(AwsSigV4Signer.HEADER_AUTHORIZATION, signedHeaders.authorization())
            .build();

    val response = http.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() / 100 == 2) {
      return true;
    }
    if (response.statusCode() == HTTP_CONFLICT && isAlreadyOwned(response.body())) {
      return false;
    }
    // Do not include the raw response body or credentials in the exception.
    throw new IOException("S3 bucket creation failed with HTTP " + response.statusCode());
  }

  private static URI bucketUri(final S3Properties s3) {
    val endpoint = URI.create(requireNonBlank(s3.endpoint(), "endpoint"));
    if (!("http".equalsIgnoreCase(endpoint.getScheme())
            || "https".equalsIgnoreCase(endpoint.getScheme()))
        || endpoint.getHost() == null
        || endpoint.getRawUserInfo() != null
        || endpoint.getRawQuery() != null
        || endpoint.getRawFragment() != null
        || (endpoint.getRawPath() != null
            && !endpoint.getRawPath().isEmpty()
            && !"/".equals(endpoint.getRawPath()))) {
      throw new IllegalArgumentException(
          "S3 endpoint must be an HTTP(S) origin with no path, query, fragment, or userinfo");
    }

    val bucket = requireNonBlank(s3.bucket(), "bucket");
    if (!BUCKET_NAME.matcher(bucket).matches()) {
      throw new IllegalArgumentException("Invalid S3 bucket name (expected a DNS-style name)");
    }
    // The endpoint is restricted to an origin, so the request and canonical URI always match.
    return URI.create(endpoint.getScheme() + "://" + endpoint.getRawAuthority() + "/" + bucket);
  }

  private static String requireNonBlank(final String value, final String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("S3 " + name + " must not be blank");
    }
    return value;
  }

  private static Duration positiveTimeout(final Duration value, final String name) {
    if (value == null || value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException("S3 " + name + " timeout must be positive");
    }
    return value;
  }

  /** Read the S3 error code, rather than searching for a substring in arbitrary XML. */
  private static boolean isAlreadyOwned(final String responseBody) {
    if (responseBody == null || responseBody.isBlank()) {
      return false;
    }
    try {
      val factory = XMLInputFactory.newFactory();
      factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
      factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
      val reader = factory.createXMLStreamReader(new StringReader(responseBody));
      try {
        return containsS3ErrorCode(reader);
      } finally {
        reader.close();
      }
    } catch (XMLStreamException | IllegalArgumentException e) {
      // An unparseable error body is not proof that the bucket already exists.
      return false;
    }
  }

  private static boolean containsS3ErrorCode(final javax.xml.stream.XMLStreamReader reader)
      throws XMLStreamException {
    if (!reader.hasNext()
        || reader.nextTag() != XMLStreamConstants.START_ELEMENT
        || !"Error".equals(reader.getLocalName())) {
      return false;
    }
    while (reader.hasNext()) {
      if (reader.next() == XMLStreamConstants.START_ELEMENT
          && "Code".equals(reader.getLocalName())) {
        return ALREADY_OWNED.equals(reader.getElementText().trim());
      }
    }
    return false;
  }
}
