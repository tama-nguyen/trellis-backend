package io.trellis.platform.aspect.feature;

import java.util.Set;

/** Answers flag lookups by name. The app's typed {@code FeatureFlags} record implements it. */
public interface FeatureFlagSource {
    boolean isEnabled(String flag);

    /** Every declared flag name, so unknown names in annotations fail startup. */
    Set<String> flagNames();
}
