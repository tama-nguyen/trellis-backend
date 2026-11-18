package io.trellis.app.config;

import io.trellis.platform.aspect.feature.FeatureFlagSource;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Every feature flag, typed. Adding a flag means adding a component here, a default in {@code application.yml}
 * with owner and sunset, and (if the browser needs it) {@code clientVisible: true} plus the same key in
 * {@code web/src/shared/lib/featureFlags.ts}. Flag names are the component names (camelCase), shared with web.
 *
 * <p>A flag past its sunset date is logged as a warning at startup: remove it or extend it deliberately.
 *
 * @param noticeSearch sample flag: search box on the notice list (client visible)
 * @param opsApi operator endpoints for DLQ replay and read-model rebuild
 */
@ConfigurationProperties(prefix = "trellis.features")
public record FeatureFlags(
        @DefaultValue Flag noticeSearch, @DefaultValue Flag opsApi) implements FeatureFlagSource {

    public static final String NOTICE_SEARCH = "noticeSearch";
    public static final String OPS_API = "opsApi";

    /**
     * @param enabled current state; the default must be the safe value
     * @param owner team or person who removes the flag
     * @param sunset date after which the flag should be gone
     * @param clientVisible whether {@code GET /api/app-config} exposes it to browsers
     */
    public record Flag(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("unowned") String owner,
            LocalDate sunset,
            @DefaultValue("false") boolean clientVisible) {}

    /** Flags by name, in declaration order. */
    public Map<String, Flag> byName() {
        Map<String, Flag> flags = new LinkedHashMap<>();
        flags.put(NOTICE_SEARCH, noticeSearch);
        flags.put(OPS_API, opsApi);
        return flags;
    }

    @Override
    public boolean isEnabled(final String flag) {
        Flag value = byName().get(flag);
        return value != null && value.enabled();
    }

    @Override
    public Set<String> flagNames() {
        return byName().keySet();
    }

    /** Only client-visible flags, name to enabled. */
    public Map<String, Boolean> clientFlags() {
        Map<String, Boolean> visible = new LinkedHashMap<>();
        byName().forEach((name, flag) -> {
            if (Objects.requireNonNull(flag, name).clientVisible()) {
                visible.put(name, flag.enabled());
            }
        });
        return visible;
    }
}
