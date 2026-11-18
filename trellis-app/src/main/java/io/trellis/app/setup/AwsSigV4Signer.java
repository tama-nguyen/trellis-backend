package io.trellis.app.setup;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** SigV4 signing for the one supported operation: an empty-body, path-style PUT to a bucket URI. */
final class AwsSigV4Signer {
    private static final String SERVICE = "s3";
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    /** Header names are declared once here: they are both sent and signed, and must agree. */
    static final String HEADER_CONTENT_SHA256 = "x-amz-content-sha256";

    static final String HEADER_DATE = "x-amz-date";
    static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_HOST = "host";
    private static final String SIGNED_HEADERS = String.join(";", HEADER_HOST, HEADER_CONTENT_SHA256, HEADER_DATE);
    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private AwsSigV4Signer() {}

    static SignedHeaders signEmptyPut(URI uri, S3Properties s3, Instant timestamp) {
        String amzDate = AMZ_DATE.format(timestamp);
        String dateStamp = DATE.format(timestamp);
        String credentialScope = dateStamp + "/" + s3.region() + "/" + SERVICE + "/aws4_request";

        String signature = hex(hmac(
                deriveSigningKey(s3.secretKey(), dateStamp, s3.region()),
                stringToSign(amzDate, credentialScope, canonicalRequest(uri, amzDate))));

        return new SignedHeaders(
                amzDate,
                EMPTY_SHA256,
                ALGORITHM
                        + " Credential=" + s3.accessKey() + "/" + credentialScope
                        + ", SignedHeaders=" + SIGNED_HEADERS
                        + ", Signature=" + signature);
    }

    /** Step 1 of SigV4: the request as AWS canonicalises it, one element per line. */
    private static String canonicalRequest(URI uri, String amzDate) {
        return String.join(
                "\n",
                "PUT",
                uri.getRawPath(),
                "", // no query string
                HEADER_HOST + ":" + uri.getRawAuthority(),
                HEADER_CONTENT_SHA256 + ":" + EMPTY_SHA256,
                HEADER_DATE + ":" + amzDate,
                "", // blank line closes the headers
                SIGNED_HEADERS,
                EMPTY_SHA256);
    }

    /** Step 2: what actually gets signed. */
    private static String stringToSign(String amzDate, String credentialScope, String canonicalRequest) {
        return String.join("\n", ALGORITHM, amzDate, credentialScope, hex(sha256(canonicalRequest)));
    }

    private static byte[] deriveSigningKey(final String secretKey, final String date, final String region) {
        byte[] dateKey = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] regionKey = hmac(dateKey, region);
        byte[] serviceKey = hmac(regionKey, SERVICE);
        return hmac(serviceKey, "aws4_request");
    }

    private static byte[] sha256(final String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static byte[] hmac(final byte[] key, final String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }

    private static String hex(final byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    record SignedHeaders(String amzDate, String payloadHash, String authorization) {}
}
