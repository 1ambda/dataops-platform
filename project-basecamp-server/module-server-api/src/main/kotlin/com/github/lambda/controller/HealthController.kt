package com.github.lambda.controller

import com.github.lambda.common.constant.CommonConstants
import com.github.lambda.common.enums.HealthStatus
import com.github.lambda.domain.service.HealthService
import com.github.lambda.dto.ApiResponse
import com.github.lambda.dto.health.ExtendedHealthResponse
import com.github.lambda.dto.health.HealthResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.boot.info.BuildProperties
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

/**
 * 헬스체크 및 시스템 정보 API 컨트롤러
 */
@RestController
@RequestMapping(CommonConstants.Api.BASE_PATH)
@Tag(name = "Health", description = "시스템 상태 확인 API")
class HealthController(
    private val buildProperties: BuildProperties? = null,
    private val healthService: HealthService,
) {
    companion object {
        private const val API_VERSION = "v1"
    }

    /**
     * 간단한 핑 체크 (기존 호환성 유지)
     */
    @Operation(summary = "헬스체크", description = "서비스 상태를 확인합니다. (레거시 엔드포인트)")
    @GetMapping(CommonConstants.Api.HEALTH_PATH)
    fun health(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val healthInfo =
            mutableMapOf<String, Any>(
                "status" to "UP",
                "timestamp" to LocalDateTime.now(),
                "service" to "dataops-basecamp-server",
            )

        buildProperties?.let { build ->
            healthInfo["version"] = build.version ?: "unknown"
            healthInfo["buildTime"] = build.time ?: "unknown"
        }

        return ResponseEntity.ok(ApiResponse.success(healthInfo))
    }

    /**
     * 컴포넌트별 상세 헬스체크
     */
    @Operation(summary = "상세 헬스체크", description = "데이터베이스, Redis, Airflow 등 개별 컴포넌트 상태를 확인합니다.")
    @GetMapping("/v1/health")
    fun healthV1(): ResponseEntity<HealthResponse> {
        val components = healthService.checkHealth()
        val overallStatus = healthService.getOverallStatus(components)

        val response = HealthResponse.from(overallStatus, components)

        return if (overallStatus == HealthStatus.DOWN) {
            ResponseEntity.status(503).body(response)
        } else {
            ResponseEntity.ok(response)
        }
    }

    /**
     * 확장 헬스체크 (dli debug 용)
     */
    @Operation(summary = "확장 헬스체크", description = "CLI debug 명령어용 상세 진단 정보를 제공합니다.")
    @GetMapping("/v1/health/extended")
    fun healthExtended(): ResponseEntity<ExtendedHealthResponse> {
        val components = healthService.checkHealth()
        val overallStatus = healthService.getOverallStatus(components)

        val response =
            ExtendedHealthResponse.from(
                overallStatus = overallStatus,
                components = components,
                apiVersion = API_VERSION,
                buildVersion = buildProperties?.version,
            )

        return if (overallStatus == HealthStatus.DOWN) {
            ResponseEntity.status(503).body(response)
        } else {
            ResponseEntity.ok(response)
        }
    }

    /**
     * 서비스 정보
     */
    @Operation(summary = "서비스 정보", description = "서비스 정보를 조회합니다.")
    @GetMapping("/info")
    fun info(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val info =
            mutableMapOf<String, Any>(
                "name" to "DataOps Basecamp Server",
                "description" to "데이터 파이프라인 관리 및 실행을 위한 중앙 서비스",
                "timestamp" to LocalDateTime.now(),
            )

        buildProperties?.let { build ->
            info["build"] =
                mapOf(
                    "version" to (build.version ?: "unknown"),
                    "time" to (build.time ?: "unknown"),
                    "group" to (build.group ?: "unknown"),
                    "artifact" to (build.artifact ?: "unknown"),
                )
        }

        return ResponseEntity.ok(ApiResponse.success(info))
    }
}
