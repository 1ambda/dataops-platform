package com.dataops.basecamp.controller

import com.dataops.basecamp.domain.service.AdHocExecutionService
import com.dataops.basecamp.domain.service.ExecutionPolicyService
import com.dataops.basecamp.domain.service.ResultStorageService
import com.dataops.basecamp.dto.run.ExecuteSqlRequest
import com.dataops.basecamp.dto.run.ExecutionPolicyResponseDto
import com.dataops.basecamp.dto.run.ExecutionResultResponseDto
import com.dataops.basecamp.mapper.RunMapper
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Run REST API Controller
 *
 * Ad-Hoc SQL 실행 기능을 제공하는 REST API 컨트롤러입니다.
 * - GET /api/v1/run/policy (실행 정책 조회)
 * - POST /api/v1/run/execute (SQL 실행)
 * - GET /api/v1/run/results/{queryId}/download (결과 다운로드)
 */
@RestController
@RequestMapping("/api/v1/run")
@Validated
class RunController(
    private val adHocExecutionService: AdHocExecutionService,
    private val executionPolicyService: ExecutionPolicyService,
    private val resultStorageService: ResultStorageService,
) {
    /**
     * 실행 정책 조회
     *
     * 현재 사용자의 ad-hoc 실행 정책 및 사용량을 반환합니다.
     * CLI의 dli run 명령어에서 사전 검증에 사용됩니다.
     *
     * @param authentication 인증 정보
     * @return 실행 정책 및 현재 사용량
     */
    @GetMapping("/policy")
    fun getPolicy(authentication: Authentication?): ResponseEntity<ExecutionPolicyResponseDto> {
        val userId = authentication?.name ?: "anonymous"
        val policy = executionPolicyService.getPolicy(userId)
        val responseDto = RunMapper.toResponseDto(policy)

        return ResponseEntity.ok(responseDto)
    }

    /**
     * Ad-Hoc SQL 실행
     *
     * SQL 쿼리를 실행하고 결과를 반환합니다.
     * dryRun=true인 경우 검증만 수행합니다.
     *
     * @param request SQL 실행 요청
     * @param authentication 인증 정보
     * @return 실행 결과
     */
    @PostMapping("/execute")
    fun executeSQL(
        @Valid @RequestBody request: ExecuteSqlRequest,
        authentication: Authentication?,
    ): ResponseEntity<ExecutionResultResponseDto> {
        val userId = authentication?.name ?: "anonymous"

        // Execute SQL
        val result =
            adHocExecutionService.executeSQL(
                userId = userId,
                sql = request.sql,
                engine = request.engine,
                parameters = request.parameters,
                downloadFormat = request.downloadFormat,
                dryRun = request.dryRun,
            )

        // Store results and get download URLs (if not dry run and has rows)
        val queryId = result.queryId
        val downloadUrls =
            if (!request.dryRun && queryId != null && result.rows.isNotEmpty()) {
                resultStorageService.storeResults(
                    queryId = queryId,
                    rows = result.rows,
                    downloadFormat = request.downloadFormat,
                )
            } else {
                emptyMap()
            }

        val responseDto = RunMapper.toResponseDto(result, downloadUrls)

        return ResponseEntity.ok(responseDto)
    }

    /**
     * 결과 다운로드
     *
     * 실행된 쿼리의 결과를 다운로드합니다.
     * 현재 CSV 형식만 지원됩니다.
     *
     * @param queryId 쿼리 ID
     * @param format 다운로드 형식 (csv)
     * @param token 다운로드 토큰
     * @return CSV 파일
     */
    @GetMapping("/results/{queryId}/download")
    fun downloadResult(
        @PathVariable queryId: String,
        @RequestParam format: String,
        @RequestParam token: String,
    ): ResponseEntity<ByteArray> {
        val content = resultStorageService.getResultForDownload(queryId, format, token)

        val contentType =
            when (format.lowercase()) {
                "csv" -> "text/csv"
                else -> "application/octet-stream"
            }

        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"result.$format\"")
            .header(HttpHeaders.CONTENT_LENGTH, content.size.toString())
            .body(content)
    }
}
