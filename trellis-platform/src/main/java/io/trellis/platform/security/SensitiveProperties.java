package io.trellis.platform.security;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Classification of every {@code trellis.*} configuration property as sensitive (redacted in {@code
 * /actuator/env} and {@code /actuator/configprops}) or not. A guardrail test fails when a property
 * is in neither list or in both, so adding a property forces a conscious decision (after DataHub's
 * {@code PropertiesCollectorConfigurationTest}). Never disable that test.
 *
 * <p>Patterns: {@code *} matches one key segment, {@code **} any remaining segments; list indexes
 * ({@code [0]}) are ignored when matching.
 */
public final class SensitiveProperties {

  /** Secrets, credentials, connection strings that may embed credentials. */
  public static final List<String> SENSITIVE =
      List.of(
          "trellis.pagination.cursor-secret",
          "trellis.rate-limit.redis.uri",
          "trellis.rate-limit.key-secret",
          "trellis.identity.mail.encryption-key",
          "trellis.storage.s3.secret-key");

  /** Safe to show to an operator. */
  public static final List<String> NON_SENSITIVE =
      List.of(
          "trellis.app.**",
          "trellis.web.**",
          "trellis.observability.**",
          "trellis.persistence.**",
          "trellis.migration.**",
          "trellis.security.issuer-uri",
          "trellis.security.jwk-set-uri",
          "trellis.security.audiences",
          "trellis.security.roles-claim",
          "trellis.security.permissions-claim",
          "trellis.security.service-claim",
          "trellis.security.grants.**",
          "trellis.security.public-paths",
          "trellis.security.cors.**",
          "trellis.security.headers.**",
          "trellis.identity.*",
          "trellis.identity.mail.key-version",
          "trellis.identity.mail.lease",
          "trellis.identity.mail.retry-delay",
          "trellis.identity.mail.max-attempts",
          "trellis.identity.mail.worker-enabled",
          "trellis.identity.mail.poll-interval",
          "trellis.idempotency.**",
          "trellis.lock.**",
          "trellis.rate-limit.store",
          "trellis.rate-limit.cache-size",
          "trellis.rate-limit.namespace",
          "trellis.rate-limit.revision",
          "trellis.rate-limit.policies.**",
          "trellis.rate-limit.http.**",
          "trellis.events.**",
          "trellis.graphql.**",
          "trellis.features.**",
          "trellis.setup.**",
          "trellis.storage.s3.endpoint",
          "trellis.storage.s3.region",
          "trellis.storage.s3.bucket",
          "trellis.storage.s3.access-key");

  private static final Pattern LIST_INDEX = Pattern.compile("\\[\\d+]");

  private SensitiveProperties() {}

  public enum Classification {
    SENSITIVE,
    NON_SENSITIVE,
    UNCLASSIFIED,
    AMBIGUOUS
  }

  public static Classification classify(String key) {
    String normalized = LIST_INDEX.matcher(key).replaceAll("");
    boolean sensitive = SENSITIVE.stream().anyMatch(p -> matches(p, normalized));
    boolean safe = NON_SENSITIVE.stream().anyMatch(p -> matches(p, normalized));
    if (sensitive && safe) {
      return Classification.AMBIGUOUS;
    }
    if (sensitive) {
      return Classification.SENSITIVE;
    }
    return safe ? Classification.NON_SENSITIVE : Classification.UNCLASSIFIED;
  }

  public static boolean isSensitive(String key) {
    Classification c = classify(key);
    // Unknown and ambiguous keys are redacted too: fail closed if the guardrail test was skipped.
    return c != Classification.NON_SENSITIVE;
  }

  static boolean matches(String pattern, String key) {
    if (pattern.endsWith(".**") && key.equals(pattern.substring(0, pattern.length() - 3))) {
      return true; // "a.b.**" also covers the map/list property "a.b" itself
    }
    StringBuilder regex = new StringBuilder("^");
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (c == '*' && i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
        regex.append(".*");
        i++;
      } else if (c == '*') {
        regex.append("[^.]*");
      } else {
        regex.append(Pattern.quote(String.valueOf(c)));
      }
    }
    return key.matches(regex.append('$').toString());
  }
}
