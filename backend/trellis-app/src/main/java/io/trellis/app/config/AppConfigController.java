package io.trellis.app.config;

import io.trellis.platform.web.security.PublicEndpoint;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/app-config}: the configuration the browser may see, today the client-visible feature flags.
 * Public and cacheable because the web app needs it before sign-in. Shape matches {@code AppConfig} in
 * {@code web/src/shared/lib/featureFlags.ts}.
 */
@RestController
class AppConfigController {
    static final String PATH = "/api/app-config";
    private static final Duration MAX_AGE = Duration.ofSeconds(60);

    private final FeatureFlags flags;

    AppConfigController(FeatureFlags flags) {
        this.flags = flags;
    }

    @PublicEndpoint
    @GetMapping(PATH)
    ResponseEntity<AppConfig> appConfig() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(MAX_AGE).cachePublic())
                .body(new AppConfig(flags.clientFlags()));
    }

    record AppConfig(Map<String, Boolean> featureFlags) {}
}
