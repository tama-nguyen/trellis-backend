package io.trellis.identity;

import io.trellis.identity.application.port.PasswordHasher;
import io.trellis.identity.application.port.TokenCodec;
import io.trellis.identity.infrastructure.security.BcryptPasswordHasher;
import io.trellis.identity.infrastructure.security.SecureTokenCodec;
import java.security.SecureRandom;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Registers the native identity lifecycle; Keycloak JWT authentication remains available in parallel. */
@AutoConfiguration
@EnableConfigurationProperties(IdentityProperties.class)
@ComponentScan(
        basePackages = {
            "io.trellis.identity.application",
            "io.trellis.identity.infrastructure",
            "io.trellis.identity.web"
        })
public class IdentityModule {
    @Bean
    SecureRandom identitySecureRandom() {
        return new SecureRandom();
    }

    @Bean
    TokenCodec secureTokenCodec(SecureRandom random) {
        return new SecureTokenCodec(random);
    }

    @Bean
    PasswordHasher passwordHasher(IdentityProperties properties) {
        return new BcryptPasswordHasher(new BCryptPasswordEncoder(properties.bcryptStrength()));
    }
}
