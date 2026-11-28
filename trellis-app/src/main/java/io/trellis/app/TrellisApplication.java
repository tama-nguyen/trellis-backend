package io.trellis.app;

import io.trellis.app.config.FeatureFlags;
import io.trellis.app.setup.S3Properties;
import io.trellis.app.setup.SetupProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@Import({FeatureFlags.class, S3Properties.class, SetupProperties.class})
@EnableScheduling
public class TrellisApplication {
  public static void main(String[] args) {
    SpringApplication.run(TrellisApplication.class, args);
  }
}
