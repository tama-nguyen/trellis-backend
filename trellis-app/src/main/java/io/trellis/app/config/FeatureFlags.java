package io.trellis.app.config;

import io.trellis.platform.aspect.feature.FeatureFlagSource;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;

/**
 * Every feature flag, typed. Adding a flag means adding a component here, a default in {@code
 * application.yml} with owner and sunset, and (if the browser needs it) {@code clientVisible: true}
 * plus the same key in {@code web/src/shared/lib/featureFlags.ts}. Flag names are the component
 * names (camelCase), shared with web.
 *
 * <p>A flag past its sunset date is logged as a warning at startup: remove it or extend it
 * deliberately.
 *
 * <p>notificationSearch: sample flag: search box on the notification list (client visible) opsApi:
 * operator endpoints for DLQ replay and read-model rebuild
 */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
public final class FeatureFlags implements FeatureFlagSource {
  @Value("${trellis.features.notification-search.enabled:false}")
  private boolean notificationSearchEnabled;

  @Value("${trellis.features.notification-search.owner:unowned}")
  private String notificationSearchOwner;

  @Value("${trellis.features.notification-search.sunset:}")
  private LocalDate notificationSearchSunset;

  @Value("${trellis.features.notification-search.client-visible:false}")
  private boolean notificationSearchClientVisible;

  @Value("${trellis.features.ops-api.enabled:false}")
  private boolean opsApiEnabled;

  @Value("${trellis.features.ops-api.owner:unowned}")
  private String opsApiOwner;

  @Value("${trellis.features.ops-api.sunset:}")
  private LocalDate opsApiSunset;

  @Value("${trellis.features.ops-api.client-visible:false}")
  private boolean opsApiClientVisible;

  public FeatureFlags(Flag notificationSearch, Flag opsApi) {
    this.notificationSearchEnabled = notificationSearch.enabled();
    this.notificationSearchOwner = notificationSearch.owner();
    this.notificationSearchSunset = notificationSearch.sunset();
    this.notificationSearchClientVisible = notificationSearch.clientVisible();
    this.opsApiEnabled = opsApi.enabled();
    this.opsApiOwner = opsApi.owner();
    this.opsApiSunset = opsApi.sunset();
    this.opsApiClientVisible = opsApi.clientVisible();
  }

  public Flag notificationSearch() {
    return new Flag(
        notificationSearchEnabled,
        notificationSearchOwner,
        notificationSearchSunset,
        notificationSearchClientVisible);
  }

  public Flag opsApi() {
    return new Flag(opsApiEnabled, opsApiOwner, opsApiSunset, opsApiClientVisible);
  }

  public static final String NOTIFICATION_SEARCH = "notificationSearch";
  public static final String OPS_API = "opsApi";

  /**
   * enabled: current state; the default must be the safe value owner: team or person who removes
   * the flag sunset: date after which the flag should be gone clientVisible: whether {@code GET
   * /api/app-config} exposes it to browsers
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class Flag {
    private final boolean enabled;
    private final String owner;
    private final LocalDate sunset;
    private final boolean clientVisible;
  }

  /** Flags by name, in declaration order. */
  public Map<String, Flag> byName() {
    Map<String, Flag> flags = new LinkedHashMap<>();
    flags.put(NOTIFICATION_SEARCH, notificationSearch());
    flags.put(OPS_API, opsApi());
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
    byName()
        .forEach(
            (name, flag) -> {
              if (Objects.requireNonNull(flag, name).clientVisible()) {
                visible.put(name, flag.enabled());
              }
            });
    return visible;
  }
}
