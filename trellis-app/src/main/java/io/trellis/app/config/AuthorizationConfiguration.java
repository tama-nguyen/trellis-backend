package io.trellis.app.config;

import io.trellis.app.platform.application.port.GrantResourceLookup;
import io.trellis.app.platform.application.support.GrantTargetValidator;
import io.trellis.identity.application.port.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composes grant validation with resource lookups keyed by authorization scope type. */
@Configuration(proxyBeanMethods = false)
public class AuthorizationConfiguration {

  @Bean
  GrantTargetValidator grantTargetValidator(
      final UserRepository userRepository, final List<GrantResourceLookup> resourceLookups) {
    final Map<String, GrantResourceLookup> lookupsByScope =
        resourceLookups.stream()
            .collect(
                Collectors.toUnmodifiableMap(GrantResourceLookup::scopeType, Function.identity()));

    return new GrantTargetValidator(userRepository, lookupsByScope);
  }
}
