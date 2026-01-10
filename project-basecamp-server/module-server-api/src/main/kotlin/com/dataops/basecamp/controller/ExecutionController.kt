package com.dataops.basecamp.controller

import com.dataops.basecamp.annotation.AuditExcludeKeys
import com.dataops.basecamp.domain.service.ExecutionService
import com.dataops.basecamp.dto.execution.DatasetExecutionRequest
import com.dataops.basecamp.dto.execution.ExecutionResultDto
import com.dataops.basecamp.dto.execution.MetricExecutionRequest
import com.dataops.basecamp.dto.execution.QualityExecutionRequest
import com.dataops.basecamp.dto.execution.QualityExecutionResultDto
import com.dataops.basecamp.dto.execution.SqlExecutionRequest
import com.dataops.basecamp.mapper.ExecutionMapper
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Execution REST API Controller
 *
 * CLI에서 렌더링된 SQL을 받아 실행하는 REST API 컨트롤러입니다.
 * - POST /api/v1/execution/datasets/run (Dataset SQL 실행)
 * - POST /api/v1/execution/metrics/run (Metric SQL 실행)
 * - POST /api/v1/execution/quality/run (Quality SQL 실행)
 * - POST /api/v1/execution/sql/run (Ad-hoc SQL 실행)
 */
@RestController
@RequestMapping("/api/v1/execution")
@Validated
class ExecutionController(
    private val executionService: ExecutionService,
) {
    /**
     * CLI에서 렌더링된 Dataset SQL 실행
     *
     * CLI에서 미리 렌더링된 Dataset SQL을 받아 실행합니다.
     *
     * @param request Dataset 실행 요청
     * @param authentication 인증 정보
     * @return 실행 결과
     */
    @PostMapping("/datasets/run")
    @AuditExcludeKeys(["rendered_sql", "renderedSql", "original_spec", "originalSpec"])
    fun executeDataset(
        @Valid @RequestBody request: DatasetExecutionRequest,
        authentication: Authentication?,
    ): ResponseEntity<ExecutionResultDto> {
        val userId = extractUserId(authentication)
        val params = ExecutionMapper.toParams(request, userId)
        val result = executionService.executeRenderedDatasetSql(params)
        val responseDto = ExecutionMapper.toDto(result)

        return ResponseEntity.ok(responseDto)
    }

    /**
     * CLI에서 렌더링된 Metric SQL 실행
     *
     * CLI에서 미리 렌더링된 Metric SQL을 받아 실행합니다.
     *
     * @param request Metric 실행 요청
     * @param authentication 인증 정보
     * @return 실행 결과
     */
    @PostMapping("/metrics/run")
    @AuditExcludeKeys(["rendered_sql", "renderedSql", "original_spec", "originalSpec"])
    fun executeMetric(
        @Valid @RequestBody request: MetricExecutionRequest,
        authentication: Authentication?,
    ): ResponseEntity<ExecutionResultDto> {
        val userId = extractUserId(authentication)
        val params = ExecutionMapper.toParams(request, userId)
        val result = executionService.executeRenderedMetricSql(params)
        val responseDto = ExecutionMapper.toDto(result)

        return ResponseEntity.ok(responseDto)
    }

    /**
     * CLI에서 렌더링된 Quality SQL 실행
     *
     * CLI에서 미리 렌더링된 Quality 테스트 SQL들을 받아 실행합니다.
     *
     * @param request Quality 실행 요청
     * @param authentication 인증 정보
     * @return 실행 결과
     */
    @PostMapping("/quality/run")
    @AuditExcludeKeys(["rendered_sql", "renderedSql", "tests"])
    fun executeQuality(
        @Valid @RequestBody request: QualityExecutionRequest,
        authentication: Authentication?,
    ): ResponseEntity<QualityExecutionResultDto> {
        val userId = extractUserId(authentication)
        val params = ExecutionMapper.toParams(request, userId)
        val result = executionService.executeRenderedQualitySql(params)
        val responseDto = ExecutionMapper.toDto(result)

        return ResponseEntity.ok(responseDto)
    }

    /**
     * Ad-hoc SQL 실행
     *
     * CLI에서 전달받은 Ad-hoc SQL을 실행합니다.
     *
     * @param request SQL 실행 요청
     * @param authentication 인증 정보
     * @return 실행 결과
     */
    @PostMapping("/sql/run")
    @AuditExcludeKeys(["sql"])
    fun executeSql(
        @Valid @RequestBody request: SqlExecutionRequest,
        authentication: Authentication?,
    ): ResponseEntity<ExecutionResultDto> {
        val userId = extractUserId(authentication)
        val params = ExecutionMapper.toParams(request, userId)
        val result = executionService.executeRenderedAdHocSql(params)
        val responseDto = ExecutionMapper.toDto(result)

        return ResponseEntity.ok(responseDto)
    }

    /**
     * 인증 정보에서 사용자 ID 추출
     */
    private fun extractUserId(authentication: Authentication?): Long = authentication?.name?.toLongOrNull() ?: 0L
}
