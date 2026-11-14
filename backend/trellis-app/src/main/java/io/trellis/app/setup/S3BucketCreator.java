package io.trellis.app.setup;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Creates an S3 bucket with one SigV4-signed {@code PUT /<bucket>} (path-style, as MinIO expects). Written by
 * hand so the backend needs no AWS SDK until a storage module exists; 409 "already owned by you" counts as
 * success.
 */
final class S3BucketCreator {
    private static final String SERVICE = "s3";
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final int CONFLICT = 409;
    private static final String ALREADY_OWNED = "BucketAlreadyOwnedByYou";

    private final HttpClient http = HttpClient.newHttpClient();
    private final Clock clock;

    S3BucketCreator(Clock clock) {
        this.clock = clock;
    }

    /** @return true if created, false if it already existed */
    boolean createBucket(S3Properties s3) throws IOException, InterruptedException {
        URI uri = URI.create(stripSlash(s3.endpoint()) + "/" + s3.bucket());
        String host = uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
        var now = clock.instant();
        String amzDate = AMZ_DATE.format(now);
        String date = DATE.format(now);
        String canonicalHeaders =
                "host:" + host + "\nx-amz-content-sha256:" + EMPTY_SHA256 + "\nx-amz-date:" + amzDate + "\n";
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalRequest =
                "PUT\n/" + s3.bucket() + "\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + EMPTY_SHA256;
        String scope = date + "/" + s3.region() + "/" + SERVICE + "/aws4_request";
        String toSign = ALGORITHM + "\n" + amzDate + "\n" + scope + "\n" + hex(sha256(canonicalRequest));
        byte[] key = hmac(("AWS4" + s3.secretKey()).getBytes(StandardCharsets.UTF_8), date);
        key = hmac(key, s3.region());
        key = hmac(key, SERVICE);
        key = hmac(key, "aws4_request");
        String signature = hex(hmac(key, toSign));
        String authorization = ALGORITHM + " Credential=" + s3.accessKey() + "/" + scope + ", SignedHeaders="
                + signedHeaders + ", Signature=" + signature;

        HttpRequest request = HttpRequest.newBuilder(uri)
                .PUT(HttpRequest.BodyPublishers.noBody())
                .header("x-amz-content-sha256", EMPTY_SHA256)
                .header("x-amz-date", amzDate)
                .header("Authorization", authorization)
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 == 2) {
            return true;
        }
        if (response.statusCode() == CONFLICT && response.body().contains(ALREADY_OWNED)) {
            return false;
        }
        throw new IOException("Bucket creation failed with HTTP " + response.statusCode());
    }

    private static String stripSlash(String endpoint) {
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
