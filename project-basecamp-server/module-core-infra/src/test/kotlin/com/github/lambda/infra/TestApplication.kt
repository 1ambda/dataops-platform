package com.github.lambda.infra

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Test application configuration for module-core-infra tests.
 *
 * Provides the Spring Boot context needed for @DataJpaTest slices.
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = ["com.github.lambda.infra.repository"])
@EntityScan(basePackages = ["com.github.lambda.domain.model"])
class TestApplication
