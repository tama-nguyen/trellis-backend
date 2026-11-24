package io.trellis.common.config;

/**
 * The root every configuration key of this product sits under.
 *
 * <p>It exists so that code which has to recognise "our" keys at runtime - the actuator sanitizer, for one -
 * asks this class instead of repeating a literal. The {@code init} script renames the product in one pass, and
 * a literal it did not know about is exactly the kind of thing that survives a rename and then silently stops
 * matching anything.
 *
 * <p>Spring annotations are the one place that keeps naming the prefix itself, because an annotation argument
 * must be a compile-time constant: write {@code @ConfigurationProperties(prefix = ConfigKeys.ROOT + ".security")}
 * where a properties class does not already publish its own {@code PREFIX}.
 */
public final class ConfigKeys {

    /** First segment of every key this product owns. */
    public static final String ROOT = "trellis";

    /** {@link #ROOT} with its separator, for prefix tests and for building a key. */
    public static final String ROOT_PREFIX = ROOT + ".";

    private ConfigKeys() {}

    /** Whether {@code key} is one of this product's configuration keys rather than Spring's or a library's. */
    public static boolean isProductKey(String key) {
        return key != null && key.startsWith(ROOT_PREFIX);
    }
}
