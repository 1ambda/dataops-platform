package com.dataops.basecamp.infra.health

import com.dataops.basecamp.common.enums.HealthStatus
import com.dataops.basecamp.domain.external.health.HealthIndicator
import com.dataops.basecamp.domain.projection.health.ComponentHealth
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * 데이터베이스 헬스 인디케이터
 *
 * HikariCP 연결 풀 정보를 포함한 데이터베이스 상태를 체크합니다.
 */
@Component
class DatabaseHealthIndicator(
    private val dataSource: DataSource,
) : HealthIndicator {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun name(): String = "database"

    override fun check(): ComponentHealth =
        try {
            val hikariDS = dataSource as? HikariDataSource
            if (hikariDS != null) {
                val pool = hikariDS.hikariPoolMXBean
                ComponentHealth(
                    status = HealthStatus.UP,
                    details =
                        mapOf(
                            "type" to "mysql",
                            "pool" to
                                mapOf(
                                    "active" to (pool?.activeConnections ?: 0),
                                    "idle" to (pool?.idleConnections ?: 0),
                                    "max" to hikariDS.maximumPoolSize,
                                ),
                        ),
                )
            } else {
                // HikariDataSource가 아닌 경우 기본 연결 테스트
                dataSource.connection.use { connection ->
                    val isValid = connection.isValid(5)
                    if (isValid) {
                        ComponentHealth.up(mapOf("type" to "unknown"))
                    } else {
                        ComponentHealth.down("Connection validation failed")
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Database health check failed: ${e.message}")
            ComponentHealth.down(e.message)
        }
}
