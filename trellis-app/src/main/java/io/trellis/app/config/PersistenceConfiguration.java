package io.trellis.app.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Points JPA at the modules of this deployable.
 *
 * <p>Spring Boot would scan from the application's own package, which holds no entity: every
 * aggregate lives in its module ({@code io.trellis.<module>.infrastructure.persistence}). Declaring
 * the scan here, once, also stops each module from needing its own {@code @EnableJpaRepositories} -
 * several of those in one context fight over the same shared beans.
 *
 * <p>The base package is deliberately the whole product namespace rather than a list of modules:
 * adding a module should not mean editing this class. {@code
 * ArchitectureTest.jpaStaysInPersistenceAdapters()} is what keeps entities and repositories from
 * drifting out of the persistence adapters (ADR 0004).
 */
@Configuration(proxyBeanMethods = false)
@EntityScan(basePackages = PersistenceConfiguration.PRODUCT_PACKAGE)
@EnableJpaRepositories(basePackages = PersistenceConfiguration.PRODUCT_PACKAGE)
public class PersistenceConfiguration {
  /** Renamed with the project by the init script, like every other {@code io.trellis} reference. */
  static final String PRODUCT_PACKAGE = "io.trellis";
}
