package io.trellis.app.config;

import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Warns about flags past their sunset date, so flags do not become permanent clutter. */
@Component
class FeatureFlagSunsetCheck {
    private static final Logger log = LoggerFactory.getLogger(FeatureFlagSunsetCheck.class);

    private final FeatureFlags flags;
    private final Clock clock;

    FeatureFlagSunsetCheck(FeatureFlags flags, Clock clock) {
        this.flags = flags;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    void check() {
        LocalDate today = LocalDate.now(clock);
        flags.byName().forEach((name, flag) -> {
            if (flag.sunset() != null && flag.sunset().isBefore(today)) {
                log.atWarn()
                        .addKeyValue("flag", name)
                        .addKeyValue("owner", flag.owner())
                        .addKeyValue("sunset", flag.sunset())
                        .log("Feature flag is past its sunset date");
            }
        });
    }
}
