package com.github.lambda.infra.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

/**
 * JPA Auditing configuration for tests
 *
 * @DataJpaTest does not auto-configure auditing, so we need this test configuration.
 */
@TestConfiguration
@EnableJpaAuditing
class JpaTestConfig
