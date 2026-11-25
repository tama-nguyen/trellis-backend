package io.trellis.learningcontent;

import io.trellis.common.validation.CommandValidator;
import io.trellis.common.validation.ValidationPipeline;
import io.trellis.learningcontent.domain.course.model.CourseDetails;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
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
public class LearningContentModule {

  @Bean
  ValidationPipeline<CourseDetails> courseValidationPipeline(
      final List<CommandValidator<CourseDetails>> validators) {
    return new ValidationPipeline<>(validators);
  }
}
