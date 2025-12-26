package com.github.lambda.controller

import com.github.lambda.common.constant.CommonConstants
import com.github.lambda.dto.ApiResponse
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
@RequestMapping("${CommonConstants.Api.BASE_PATH}")
@Tag(name = "Health", description = "시스템 상태 확인 API")
class HealthController(
    private val buildProperties: BuildProperties? = null,
) {
    @Operation(summary = "헬스체크", description = "서비스 상태를 확인합니다.")
    @GetMapping(CommonConstants.Api.HEALTH_PATH)
    fun health(): ResponseEntity<com.github.lambda.dto.ApiResponse<Map<String, Any>>> {
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
