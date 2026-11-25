package io.trellis.notice;

import io.trellis.common.validation.CommandValidator;
import io.trellis.common.validation.ValidationPipeline;
import io.trellis.notice.domain.model.NoticeContent;
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
      "io.trellis.notice.application",
      "io.trellis.notice.infrastructure",
      "io.trellis.notice.web"
    })
public class NoticeModule {
  @Bean
  ValidationPipeline<NoticeContent> noticeValidationPipeline(
      final List<CommandValidator<NoticeContent>> validators) {
    return new ValidationPipeline<>(validators);
  }
}
