package com.dataops.basecamp.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement

/**
 * 데이터베이스 설정
 */
@Configuration
@EnableJpaRepositories(
    basePackages = ["com.dataops.basecamp.infra.repository"],
)
@EntityScan(
    basePackages = ["com.dataops.basecamp.domain.entity"],
)
@EnableJpaAuditing
@EnableTransactionManagement
@EnableConfigurationProperties(DatabaseProperties::class)
class DatabaseConfig

/**
 * 데이터베이스 설정 프로퍼티
 */
@ConfigurationProperties(prefix = "app.database")
data class DatabaseProperties(
    val connectionTimeout: Long = 30000,
    val validationTimeout: Long = 5000,
    val idleTimeout: Long = 600000,
    val maxLifetime: Long = 1800000,
    val minimumIdle: Int = 5,
    val maximumPoolSize: Int = 20,
    val poolName: String = "DataOpsHikariCP",
)
