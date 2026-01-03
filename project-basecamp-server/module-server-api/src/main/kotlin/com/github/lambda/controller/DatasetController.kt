package com.github.lambda.controller

import com.github.lambda.common.exception.*
import com.github.lambda.domain.service.DatasetService
import com.github.lambda.dto.dataset.*
import com.github.lambda.mapper.DatasetMapper
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * Dataset REST API Controller
 *
 * Dataset CRUD 및 실행 기능을 제공하는 REST API 컨트롤러입니다.
 * - GET /api/v1/datasets (목록 조회 with 필터링)
 * - GET /api/v1/datasets/{name} (상세 조회)
 * - POST /api/v1/datasets (등록)
 * - POST /api/v1/datasets/{name}/run (실행)
 */
@RestController
@RequestMapping("/api/v1/datasets")
@Validated
class DatasetController(
    private val datasetService: DatasetService,
) {
    /**
     * Dataset 목록 조회 with 필터링
     *
     * @param tag 태그 필터 (정확히 일치)
     * @param owner 소유자 필터 (부분 일치)
     * @param search 이름/설명 검색 (부분 일치)
     * @param limit 페이지 크기 (1-500)
     * @param offset 페이지 오프셋 (0부터)
     * @return 필터 조건에 맞는 Dataset 목록
     */
    @GetMapping
    fun listDatasets(
        @RequestParam(required = false) tag: String?,
        @RequestParam(required = false) owner: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "50") @Min(1) @Max(500) limit: Int,
        @RequestParam(defaultValue = "0") @Min(0) offset: Int,
    ): ResponseEntity<List<DatasetListDto>> {
        val pageRequest =
            PageRequest.of(
                offset / limit,
                limit,
                Sort.by(Sort.Direction.DESC, "updatedAt"),
            )

        val datasets = datasetService.listDatasets(tag, owner, search, pageRequest)
        val dtos = datasets.content.map { DatasetMapper.toListDto(it) }

        return ResponseEntity.ok(dtos)
    }

    /**
     * Dataset 상세 조회
     *
     * @param name Dataset 이름 (fully qualified name)
     * @return Dataset 상세 정보
     */
    @GetMapping("/{name}")
    fun getDataset(
        @PathVariable name: String,
    ): ResponseEntity<DatasetDto> {
        val dataset =
            datasetService.getDataset(name)
                ?: throw DatasetNotFoundException(name)

        val dto = DatasetMapper.toDto(dataset)
        return ResponseEntity.ok(dto)
    }

    /**
     * Dataset 등록
     *
     * @param request Dataset 생성 요청
     * @return 등록 성공 응답
     */
    @PostMapping
    fun registerDataset(
        @Valid @RequestBody request: CreateDatasetRequest,
    ): ResponseEntity<DatasetRegistrationResponse> {
        val dataset = DatasetMapper.toEntity(request)
        val savedDataset = datasetService.registerDataset(dataset)

        val response = DatasetMapper.toRegistrationResponse(savedDataset.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * Dataset 실행 (TODO: Query Engine 연동 필요)
     *
     * @param name Dataset 이름
     * @param request 실행 요청 (파라미터, limit, timeout)
     * @return 실행 결과
     */
    @PostMapping("/{name}/run")
    fun executeDataset(
        @PathVariable name: String,
        @Valid @RequestBody request: ExecuteDatasetRequest,
    ): ResponseEntity<ExecutionResultDto> {
        // Dataset 존재 여부 확인
        val dataset =
            datasetService.getDataset(name)
                ?: throw DatasetNotFoundException(name)

        // TODO: DatasetExecutionService 구현 필요
        // 현재는 mock 응답 반환
        val mockResult =
            DatasetMapper.toExecutionResult(
                rows =
                    listOf(
                        mapOf("message" to "Mock execution result for dataset: ${dataset.name}"),
                        mapOf("sql" to dataset.sql),
                        mapOf("parameters" to request.parameters),
                    ),
                durationSeconds = 1.5,
                renderedSql = renderSqlWithParameters(dataset.sql, request.parameters),
            )

        return ResponseEntity.ok(mockResult)
    }

    // === Helper Methods ===

    /**
     * SQL 템플릿에 파라미터를 적용하여 렌더링
     * TODO: 실제 템플릿 엔진 연동 필요
     */
    private fun renderSqlWithParameters(
        sql: String,
        parameters: Map<String, Any>,
    ): String {
        var rendered = sql
        parameters.forEach { (key, value) ->
            // 간단한 변수 치환 ({{variable}} 또는 ${variable} 형태)
            rendered = rendered.replace("{{$key}}", value.toString())
            rendered = rendered.replace("\${$key}", value.toString())
        }
        return rendered
    }
}
