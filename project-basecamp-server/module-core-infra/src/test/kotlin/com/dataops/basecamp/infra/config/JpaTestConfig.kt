package com.dataops.basecamp.infra.config

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

/**
 * JPA Auditing configuration for tests
 *
 * @DataJpaTest does not auto-configure auditing, so we need this test configuration.
 * @EntityScan is required for Spring Boot 4 to properly discover entities across modules.
 */
@TestConfiguration
@EnableJpaAuditing
@EntityScan(basePackages = ["com.dataops.basecamp.domain.entity"])
class JpaTestConfig
