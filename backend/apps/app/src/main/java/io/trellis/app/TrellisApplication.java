package io.trellis.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Entry point. Profiles: {@code dev}, {@code test}, {@code prod}; app mode {@code migrate} runs backfills and exits. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class TrellisApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrellisApplication.class, args);
    }
}
