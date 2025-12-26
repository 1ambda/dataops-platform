package com.github.lambda.controller

import com.github.lambda.common.constant.CommonConstants
import com.github.lambda.domain.model.pipeline.PipelineStatus
import com.github.lambda.domain.service.PipelineService
import com.github.lambda.dto.*
import com.github.lambda.mapper.PipelineMapper
import com.github.lambda.security.SecurityLevel
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * 파이프라인 관리 REST API 컨트롤러
 *
 * 엔터프라이즈 아키텍처 패턴을 적용한 컨트롤러입니다:
 * 1. Mapper를 통한 DTO ↔ Domain 변환
 * 2. Domain Service 호출로 비즈니스 로직 실행
 * 3. 보안 수준에 따른 응답 데이터 제어
 */
@RestController
@RequestMapping("${CommonConstants.Api.V1_PATH}/pipelines")
@Validated
@Tag(name = "Pipeline", description = "데이터 파이프라인 관리 API")
class PipelineController(
    private val pipelineService: PipelineService,
    private val pipelineMapper: PipelineMapper,
) {
    private val logger = KotlinLogging.logger {}

    @Operation(summary = "파이프라인 목록 조회", description = "조건에 따라 파이프라인 목록을 조회합니다.")
    @GetMapping
    fun getPipelines(
        @Parameter(description = "소유자") @RequestParam(required = false) owner: String?,
        @Parameter(description = "상태") @RequestParam(required = false) status: PipelineStatus?,
        @Parameter(description = "활성화 여부") @RequestParam(required = false) isActive: Boolean?,
        @Parameter(description = "페이지 번호") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "정렬 기준") @RequestParam(defaultValue = "updatedAt") sort: String,
        @Parameter(description = "정렬 방향") @RequestParam(defaultValue = "desc") direction: String,
    ): ResponseEntity<ApiResponse<PagedResponse<PipelineResponse>>> {
        // 1. Request 파라미터를 Domain Query로 변환
        val pageRequest =
            PageRequest.of(
                page,
                size.coerceAtMost(CommonConstants.Pagination.MAX_SIZE),
                Sort.Direction.fromString(direction),
                sort,
            )
        val query = pipelineMapper.toQuery(owner, status, isActive, pageRequest)

        // 2. Domain Service 호출
        val pipelinesPage = pipelineService.getPipelines(query)

        // 3. Domain Entity를 Response DTO로 변환
        val responseData = PagedResponse.from(pipelinesPage.map { pipelineMapper.toResponse(it) })

        return ResponseEntity.ok(ApiResponse.success(responseData))
    }

    @Operation(summary = "파이프라인 상세 조회", description = "ID로 파이프라인 상세 정보를 조회합니다.")
    @SwaggerApiResponse(responseCode = "200", description = "조회 성공")
    @SwaggerApiResponse(responseCode = "404", description = "파이프라인을 찾을 수 없음")
    @GetMapping("/{id}")
    fun getPipeline(
        @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<PipelineResponse>> {
        // 1. Request 파라미터를 Domain Query로 변환
        val query = pipelineMapper.toQuery(id, includeJobs = false)

        // 2. Domain Service 호출
        val pipelineEntity =
            pipelineService.getPipeline(query)
                ?: return ResponseEntity.notFound().build()

        // 3. Domain Entity를 Response DTO로 변환
        val response = pipelineMapper.toResponse(pipelineEntity)

        return ResponseEntity.ok(ApiResponse.success(response))
    }

    @Operation(summary = "파이프라인 생성", description = "새로운 파이프라인을 생성합니다.")
    @SwaggerApiResponse(responseCode = "201", description = "생성 성공")
    @SwaggerApiResponse(responseCode = "400", description = "잘못된 요청")
    @PostMapping
    fun createPipeline(
        @Valid @RequestBody request: CreatePipelineRequest,
    ): ResponseEntity<ApiResponse<PipelineResponse>> {
        logger.info { "Creating new pipeline: ${request.name} by ${request.owner}" }

        // 1. Request DTO를 Domain Command로 변환
        val command = pipelineMapper.toCommand(request)

        // 2. Domain Service 호출
        val createdPipelineEntity = pipelineService.createPipeline(command)

        // 3. Domain Entity를 Response DTO로 변환
        val response = pipelineMapper.toResponse(createdPipelineEntity)

        logger.info { "Pipeline created successfully: ${createdPipelineEntity.id}" }
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "파이프라인이 성공적으로 생성되었습니다."))
    }

    @Operation(summary = "파이프라인 수정", description = "기존 파이프라인 정보를 수정합니다.")
    @SwaggerApiResponse(responseCode = "200", description = "수정 성공")
    @SwaggerApiResponse(responseCode = "404", description = "파이프라인을 찾을 수 없음")
    @PutMapping("/{id}")
    fun updatePipeline(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdatePipelineRequest,
    ): ResponseEntity<ApiResponse<PipelineResponse>> {
        logger.info { "Updating pipeline: $id" }

        // 1. Request DTO를 Domain Command로 변환
        val command = pipelineMapper.toCommand(id, request)

        // 2. Domain Service 호출
        val updatedPipelineEntity = pipelineService.updatePipeline(command)

        // 3. Domain Entity를 Response DTO로 변환
        val response = pipelineMapper.toResponse(updatedPipelineEntity)

        logger.info { "Pipeline updated successfully: $id" }
        return ResponseEntity.ok(ApiResponse.success(response, "파이프라인이 성공적으로 수정되었습니다."))
    }

    @Operation(summary = "파이프라인 상태 변경", description = "파이프라인의 상태를 변경합니다.")
    @SwaggerApiResponse(responseCode = "200", description = "상태 변경 성공")
    @SwaggerApiResponse(responseCode = "404", description = "파이프라인을 찾을 수 없음")
    @PatchMapping("/{id}/status")
    fun updatePipelineStatus(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdatePipelineStatusRequest,
    ): ResponseEntity<ApiResponse<PipelineResponse>> {
        logger.info { "Updating pipeline status: $id -> ${request.status}" }

        // 1. Request DTO를 Domain Command로 변환
        val command = pipelineMapper.toCommand(id, request)

        // 2. Domain Service 호출
        val updatedPipelineEntity = pipelineService.updatePipelineStatus(command)

        // 3. Domain Entity를 Response DTO로 변환
        val response = pipelineMapper.toResponse(updatedPipelineEntity)

        logger.info { "Pipeline status updated successfully: $id" }
        return ResponseEntity.ok(ApiResponse.success(response, "파이프라인 상태가 변경되었습니다."))
    }

    @Operation(summary = "파이프라인 활성화 토글", description = "파이프라인의 활성화 상태를 토글합니다.")
    @SwaggerApiResponse(responseCode = "200", description = "토글 성공")
    @SwaggerApiResponse(responseCode = "404", description = "파이프라인을 찾을 수 없음")
    @PatchMapping("/{id}/toggle-active")
    fun togglePipelineActive(
        @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<PipelineResponse>> {
        logger.info { "Toggling pipeline active status: $id" }

        // 1. Request 파라미터를 Domain Command로 변환
        val command = pipelineMapper.toToggleCommand(id)

        // 2. Domain Service 호출
        val updatedPipelineEntity = pipelineService.togglePipelineActive(command)

        // 3. Domain Entity를 Response DTO로 변환
        val response = pipelineMapper.toResponse(updatedPipelineEntity)

        logger.info { "Pipeline active status toggled successfully: $id -> ${updatedPipelineEntity.isActive}" }
        return ResponseEntity.ok(ApiResponse.success(response, "파이프라인 활성화 상태가 변경되었습니다."))
    }

    @Operation(summary = "파이프라인 실행", description = "파이프라인을 실행합니다.")
    @SwaggerApiResponse(responseCode = "200", description = "실행 시작 성공")
    @SwaggerApiResponse(responseCode = "404", description = "파이프라인을 찾을 수 없음")
    @PostMapping("/{id}/execute")
    fun executePipeline(
        @PathVariable id: Long,
        @RequestBody(required = false) parameters: Map<String, Any>? = null,
    ): ResponseEntity<ApiResponse<PipelineExecutionResponse>> {
        logger.info { "Executing pipeline: $id" }

        // 1. Request 파라미터를 Domain Command로 변환
        val command = pipelineMapper.toExecuteCommand(id, parameters ?: emptyMap())

        // 2. Domain Service 호출
        val executionDto = pipelineService.executePipeline(command)

        // 3. Domain DTO를 Response DTO로 변환
        val response = pipelineMapper.toResponse(executionDto)

        logger.info { "Pipeline execution started: $id -> ${executionDto.executionId}" }
        return ResponseEntity.ok(ApiResponse.success(response, "파이프라인 실행이 시작되었습니다."))
    }

    @Operation(summary = "파이프라인 실행 중지", description = "실행 중인 파이프라인을 중지합니다.")
    @SwaggerApiResponse(responseCode = "200", description = "중지 성공")
    @SwaggerApiResponse(responseCode = "404", description = "파이프라인 또는 실행을 찾을 수 없음")
    @PostMapping("/{id}/stop/{executionId}")
    fun stopPipelineExecution(
        @PathVariable id: Long,
        @PathVariable executionId: String,
        @RequestParam(required = false) reason: String? = null,
    ): ResponseEntity<ApiResponse<String>> {
        logger.info { "Stopping pipeline execution: $id -> $executionId" }

        // 1. Request 파라미터를 Domain Command로 변환
        val command = pipelineMapper.toStopCommand(id, executionId, reason)

        // 2. Domain Service 호출
        val stopped = pipelineService.stopPipelineExecution(command)

        return if (stopped) {
            logger.info { "Pipeline execution stopped successfully: $id -> $executionId" }
            ResponseEntity.ok(ApiResponse.success("STOPPED", "파이프라인 실행이 중지되었습니다."))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @Operation(summary = "파이프라인 삭제", description = "파이프라인을 삭제합니다. (소프트 삭제 - 아카이브)")
    @SwaggerApiResponse(responseCode = "204", description = "삭제 성공")
    @SwaggerApiResponse(responseCode = "404", description = "파이프라인을 찾을 수 없음")
    @DeleteMapping("/{id}")
    fun deletePipeline(
        @PathVariable id: Long,
        @RequestParam(required = false) reason: String? = null,
    ): ResponseEntity<ApiResponse<Void>> {
        logger.info { "Deleting pipeline: $id" }

        // 1. Request 파라미터를 Domain Command로 변환
        // TODO: 실제로는 현재 사용자 정보를 SecurityContext에서 가져와야 함
        val deletedBy = "system" // 임시로 system으로 설정
        val command = pipelineMapper.toDeleteCommand(id, deletedBy, reason)

        // 2. Domain Service 호출
        val deleted = pipelineService.deletePipeline(command)

        return if (deleted) {
            logger.info { "Pipeline deleted successfully: $id" }
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }

    // === 보안 고려사항: 다양한 보안 레벨을 지원하는 API 추가 ===

    @Operation(summary = "파이프라인 공개 조회", description = "제한된 정보로 파이프라인을 조회합니다. (공개 API)")
    @GetMapping("/public/{id}")
    fun getPublicPipeline(
        @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<PipelineResponse>> {
        // 1. Request 파라미터를 Domain Query로 변환
        val query = pipelineMapper.toQuery(id, includeJobs = false)

        // 2. Domain Service 호출
        val pipelineEntity =
            pipelineService.getPipeline(query)
                ?: return ResponseEntity.notFound().build()

        // 3. 보안 수준을 적용한 Response DTO로 변환 (민감한 정보 제거)
        val response = pipelineMapper.toSecureResponse(pipelineEntity, SecurityLevel.PUBLIC)

        return ResponseEntity.ok(ApiResponse.success(response))
    }

    @Operation(summary = "파이프라인 통계 조회", description = "파이프라인 통계 정보를 조회합니다.")
    @GetMapping("/statistics")
    fun getPipelineStatistics(
        @RequestParam(required = false) owner: String? = null,
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        // 1. Request 파라미터를 Domain Query로 변환
        val query = pipelineMapper.toStatisticsQuery(owner)

        // 2. Domain Service 호출
        val statisticsDto = pipelineService.getPipelineStatistics(query)

        // 3. 보안 수준을 적용한 통계 응답으로 변환
        val response = pipelineMapper.toStatisticsResponse(statisticsDto, SecurityLevel.INTERNAL)

        return ResponseEntity.ok(ApiResponse.success(response))
    }
}
