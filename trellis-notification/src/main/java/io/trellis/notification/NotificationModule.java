package io.trellis.notification;

import io.trellis.common.validation.CommandValidator;
import io.trellis.common.validation.ValidationPipeline;
import io.trellis.notification.domain.model.NotificationContent;
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
      "io.trellis.notification.application",
      "io.trellis.notification.infrastructure",
      "io.trellis.notification.web"
    })
public class NotificationModule {
  @Bean
  ValidationPipeline<NotificationContent> notificationValidationPipeline(
      final List<CommandValidator<NotificationContent>> validators) {
    return new ValidationPipeline<>(validators);
  }
}
