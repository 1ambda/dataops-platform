package com.dataops.basecamp.dto.health

import com.dataops.basecamp.common.enums.HealthStatus
import com.dataops.basecamp.domain.projection.health.ComponentHealth
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime

/**
 * 기본 헬스체크 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HealthResponse(
    val status: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val timestamp: LocalDateTime,
    val components: ComponentsHealthDto,
) {
    companion object {
        fun from(
            overallStatus: HealthStatus,
            components: Map<String, ComponentHealth>,
        ): HealthResponse =
            HealthResponse(
                status = overallStatus.name,
                timestamp = LocalDateTime.now(),
                components =
                    ComponentsHealthDto(
                        database = ComponentHealthDto.from(components["database"]),
                        redis = ComponentHealthDto.from(components["redis"]),
                        airflow = ComponentHealthDto.from(components["airflow"]),
                    ),
            )
    }
}

/**
 * 컴포넌트 헬스 정보 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ComponentsHealthDto(
    val database: ComponentHealthDto,
    val redis: ComponentHealthDto,
    val airflow: ComponentHealthDto,
)

/**
 * 단일 컴포넌트 헬스 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ComponentHealthDto(
    val status: String,
    val details: Map<String, Any?>? = null,
) {
    companion object {
        fun from(health: ComponentHealth?): ComponentHealthDto =
            if (health != null) {
                ComponentHealthDto(
                    status = health.status.name,
                    details = health.details.ifEmpty { null },
                )
            } else {
                ComponentHealthDto(status = HealthStatus.UNKNOWN.name)
            }
    }
}

/**
 * 확장 헬스체크 응답 DTO (dli debug 용)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExtendedHealthResponse(
    val status: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val timestamp: LocalDateTime,
    val version: VersionInfoDto,
    val components: ExtendedComponentsHealthDto,
) {
    companion object {
        fun from(
            overallStatus: HealthStatus,
            components: Map<String, ComponentHealth>,
            apiVersion: String,
            buildVersion: String?,
        ): ExtendedHealthResponse =
            ExtendedHealthResponse(
                status = overallStatus.name,
                timestamp = LocalDateTime.now(),
                version = VersionInfoDto(api = apiVersion, build = buildVersion),
                components =
                    ExtendedComponentsHealthDto(
                        database = DatabaseHealthDto.from(components["database"]),
                        redis = RedisHealthDto.from(components["redis"]),
                        airflow = AirflowHealthDto.from(components["airflow"]),
                    ),
            )
    }
}

/**
 * 버전 정보 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class VersionInfoDto(
    val api: String,
    val build: String?,
)

/**
 * 확장 컴포넌트 헬스 정보 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExtendedComponentsHealthDto(
    val database: DatabaseHealthDto,
    val redis: RedisHealthDto,
    val airflow: AirflowHealthDto,
)

/**
 * 데이터베이스 헬스 상세 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DatabaseHealthDto(
    val status: String,
    val type: String,
    val connectionPool: ConnectionPoolInfoDto?,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(health: ComponentHealth?): DatabaseHealthDto {
            if (health == null) {
                return DatabaseHealthDto(
                    status = HealthStatus.UNKNOWN.name,
                    type = "unknown",
                    connectionPool = null,
                )
            }
            val poolDetails = health.details["pool"] as? Map<String, Any?>
            return DatabaseHealthDto(
                status = health.status.name,
                type = health.details["type"]?.toString() ?: "unknown",
                connectionPool =
                    poolDetails?.let {
                        ConnectionPoolInfoDto(
                            active = (it["active"] as? Number)?.toInt() ?: 0,
                            idle = (it["idle"] as? Number)?.toInt() ?: 0,
                            max = (it["max"] as? Number)?.toInt() ?: 0,
                        )
                    },
            )
        }
    }
}

/**
 * 연결 풀 정보 DTO
 */
data class ConnectionPoolInfoDto(
    val active: Int,
    val idle: Int,
    val max: Int,
)

/**
 * Redis 헬스 상세 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RedisHealthDto(
    val status: String,
    val mode: String?,
    val version: String?,
) {
    companion object {
        fun from(health: ComponentHealth?): RedisHealthDto =
            if (health != null) {
                RedisHealthDto(
                    status = health.status.name,
                    mode = health.details["mode"]?.toString(),
                    version = health.details["version"]?.toString(),
                )
            } else {
                RedisHealthDto(
                    status = HealthStatus.UNKNOWN.name,
                    mode = null,
                    version = null,
                )
            }
    }
}

/**
 * Airflow 헬스 상세 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AirflowHealthDto(
    val status: String,
    val version: String?,
    val dagCount: Int?,
) {
    companion object {
        fun from(health: ComponentHealth?): AirflowHealthDto =
            if (health != null) {
                AirflowHealthDto(
                    status = health.status.name,
                    version = health.details["version"]?.toString(),
                    dagCount = (health.details["dagCount"] as? Number)?.toInt(),
                )
            } else {
                AirflowHealthDto(
                    status = HealthStatus.UNKNOWN.name,
                    version = null,
                    dagCount = null,
                )
            }
    }
}
