package com.dataops.basecamp.domain.projection.health

import com.dataops.basecamp.common.enums.HealthStatus

/**
 * 개별 컴포넌트의 헬스체크 결과
 */
data class ComponentHealth(
    val status: HealthStatus,
    val details: Map<String, Any?> = emptyMap(),
) {
    companion object {
        fun up(details: Map<String, Any?> = emptyMap()) = ComponentHealth(HealthStatus.UP, details)

        fun down(
            error: String? = null,
            details: Map<String, Any?> = emptyMap(),
        ) = ComponentHealth(
            status = HealthStatus.DOWN,
            details =
                if (error != null) {
                    details + ("error" to error)
                } else {
                    details
                },
        )

        fun unknown(details: Map<String, Any?> = emptyMap()) = ComponentHealth(HealthStatus.UNKNOWN, details)
    }
}

/**
 * 연결 풀 정보
 */
data class ConnectionPoolInfo(
    val active: Int,
    val idle: Int,
    val max: Int,
)

/**
 * 데이터베이스 헬스 상세 정보
 */
data class DatabaseHealthDetails(
    val type: String,
    val connectionPool: ConnectionPoolInfo?,
)

/**
 * Redis 헬스 상세 정보
 */
data class RedisHealthDetails(
    val mode: String?,
    val version: String?,
)

/**
 * Airflow 헬스 상세 정보
 */
data class AirflowHealthDetails(
    val version: String?,
    val dagCount: Int?,
)
