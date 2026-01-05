package com.dataops.basecamp.infra.health

import com.dataops.basecamp.common.enums.HealthStatus
import com.dataops.basecamp.domain.external.health.HealthIndicator
import com.dataops.basecamp.domain.projection.health.ComponentHealth
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.stereotype.Component

/**
 * Redis 헬스 인디케이터
 *
 * Redis 연결 상태와 서버 정보를 체크합니다.
 */
@Component
class RedisHealthIndicator(
    private val redisConnectionFactory: RedisConnectionFactory,
) : HealthIndicator {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun name(): String = "redis"

    override fun check(): ComponentHealth =
        try {
            redisConnectionFactory.connection.use { connection ->
                val serverCommands = connection.serverCommands()
                val info = serverCommands.info("server")

                // Redis 버전 추출
                val version = extractRedisVersion(info)

                // 클러스터/스탠드얼론 모드 확인
                val mode = detectRedisMode(info)

                ComponentHealth(
                    status = HealthStatus.UP,
                    details =
                        mapOf(
                            "mode" to mode,
                            "version" to (version ?: "unknown"),
                        ),
                )
            }
        } catch (e: Exception) {
            logger.warn("Redis health check failed: ${e.message}")
            ComponentHealth.down(e.message)
        }

    private fun extractRedisVersion(info: java.util.Properties?): String? = info?.getProperty("redis_version")

    private fun detectRedisMode(info: java.util.Properties?): String {
        val clusterEnabled = info?.getProperty("cluster_enabled")
        return if (clusterEnabled == "1") "cluster" else "standalone"
    }
}
