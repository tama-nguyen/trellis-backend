package io.trellis.learningcontent;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Registers the module: adding the Gradle dependency to {@code trellis-app} is all the application
 * needs. Scans only the sub-packages, so this class is not picked up a second time.
 */
@AutoConfiguration
@ComponentScan(
    basePackages = {
      "io.trellis.learningcontent.application",
      "io.trellis.learningcontent.infrastructure",
      "io.trellis.learningcontent.web"
    })
public class LearningContentModule {}
