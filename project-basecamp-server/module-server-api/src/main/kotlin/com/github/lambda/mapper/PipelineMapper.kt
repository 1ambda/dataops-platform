package com.github.lambda.mapper

import com.github.lambda.domain.command.pipeline.*
import com.github.lambda.domain.model.pipeline.PipelineExecution
import com.github.lambda.domain.model.pipeline.PipelineStatistics
import com.github.lambda.domain.query.pipeline.*
import com.github.lambda.dto.*
import com.github.lambda.dto.PipelineDto
import com.github.lambda.security.FieldAccessControl
import com.github.lambda.security.SecurityLevel
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

/**
 * 파이프라인 매퍼
 *
 * API DTO와 Domain 객체 간의 변환을 담당합니다.
 * - Request DTO → Domain Command
 * - Domain DTO → Response DTO
 * - Query 파라미터 → Domain Query
 * - 보안 수준에 따른 필드 노출 제어
 */
@Component
class PipelineMapper(
    private val fieldAccessControl: FieldAccessControl,
) {
    // === Request DTO → Domain Command 변환 ===

    /**
     * 파이프라인 생성 요청을 도메인 명령으로 변환
     */
    fun toCommand(request: CreatePipelineRequest): CreatePipelineCommand =
        CreatePipelineCommand(
            name = request.name,
            description = request.description,
            owner = request.owner,
            scheduleExpression = request.scheduleExpression,
            isActive = request.isActive,
        )

    /**
     * 파이프라인 수정 요청을 도메인 명령으로 변환
     */
    fun toCommand(
        id: Long,
        request: UpdatePipelineRequest,
    ): UpdatePipelineCommand =
        UpdatePipelineCommand(
            id = id,
            name = request.name,
            description = request.description,
            scheduleExpression = request.scheduleExpression,
        )

    /**
     * 파이프라인 상태 변경 요청을 도메인 명령으로 변환
     */
    fun toCommand(
        id: Long,
        request: UpdatePipelineStatusRequest,
    ): UpdatePipelineStatusCommand =
        UpdatePipelineStatusCommand(
            id = id,
            status = request.status,
        )

    /**
     * 파이프라인 활성화 토글 명령 생성
     */
    fun toToggleCommand(id: Long): TogglePipelineActiveCommand = TogglePipelineActiveCommand(id = id)

    /**
     * 파이프라인 실행 명령 생성
     */
    fun toExecuteCommand(
        id: Long,
        parameters: Map<String, Any> = emptyMap(),
    ): ExecutePipelineCommand =
        ExecutePipelineCommand(
            id = id,
            parameters = parameters,
        )

    /**
     * 파이프라인 실행 중지 명령 생성
     */
    fun toStopCommand(
        pipelineId: Long,
        executionId: String,
        reason: String? = null,
    ): StopPipelineExecutionCommand =
        StopPipelineExecutionCommand(
            pipelineId = pipelineId,
            executionId = executionId,
            reason = reason,
        )

    /**
     * 파이프라인 삭제 명령 생성
     */
    fun toDeleteCommand(
        id: Long,
        deletedBy: String,
        reason: String? = null,
    ): DeletePipelineCommand =
        DeletePipelineCommand(
            id = id,
            deletedBy = deletedBy,
            reason = reason,
        )

    // === Query 파라미터 → Domain Query 변환 ===

    /**
     * 파이프라인 조회 쿼리 생성
     */
    fun toQuery(
        id: Long,
        includeJobs: Boolean = false,
    ): GetPipelineQuery =
        GetPipelineQuery(
            id = id,
            includeJobs = includeJobs,
            includeDeleted = false,
        )

    /**
     * 파이프라인 목록 조회 쿼리 생성
     */
    fun toQuery(
        owner: String? = null,
        status: com.github.lambda.domain.model.pipeline.PipelineStatus? = null,
        isActive: Boolean? = null,
        pageable: Pageable,
    ): GetPipelinesQuery =
        GetPipelinesQuery(
            owner = owner,
            status = status,
            isActive = isActive,
            includeDeleted = false,
            pageable = pageable,
        )

    /**
     * 소유자별 파이프라인 조회 쿼리 생성
     */
    fun toOwnerQuery(
        owner: String,
        includeInactive: Boolean = false,
        pageable: Pageable,
    ): GetPipelinesByOwnerQuery =
        GetPipelinesByOwnerQuery(
            owner = owner,
            includeInactive = includeInactive,
            pageable = pageable,
        )

    /**
     * 상태별 파이프라인 조회 쿼리 생성
     */
    fun toStatusQuery(
        status: com.github.lambda.domain.model.pipeline.PipelineStatus,
        includeInactive: Boolean = false,
        pageable: Pageable,
    ): GetPipelinesByStatusQuery =
        GetPipelinesByStatusQuery(
            status = status,
            includeInactive = includeInactive,
            pageable = pageable,
        )

    /**
     * 파이프라인 통계 조회 쿼리 생성
     */
    fun toStatisticsQuery(owner: String? = null): GetPipelineStatisticsQuery =
        GetPipelineStatisticsQuery(
            owner = owner,
            includeJobCounts = true,
            includeStatusCounts = true,
        )

    // === Domain DTO → Response DTO 변환 ===

    /**
     * 도메인 Entity를 응답 DTO로 변환 (헥사고날 아키텍처 준수)
     * Note: jobCount must be provided separately since jobs relationship was removed.
     */
    fun toResponse(
        entity: com.github.lambda.domain.model.pipeline.PipelineEntity,
        jobCount: Int = 0,
    ): PipelineResponse =
        PipelineResponse(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            status = entity.status,
            owner = entity.owner,
            scheduleExpression = entity.scheduleExpression,
            isActive = entity.isActive,
            jobCount = jobCount,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    /**
     * 도메인 DTO를 응답 DTO로 변환 (레거시 호환용 - deprecated)
     */
    @Deprecated("Use toResponse(entity: PipelineEntity) instead for hexagonal architecture compliance")
    fun toResponse(dto: PipelineDto): PipelineResponse =
        PipelineResponse(
            id = dto.id,
            name = dto.name,
            description = dto.description,
            status = dto.status,
            owner = dto.owner,
            scheduleExpression = dto.scheduleExpression,
            isActive = dto.isActive,
            jobCount = dto.jobCount,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
        )

    /**
     * 파이프라인 실행 도메인 객체를 응답 DTO로 변환
     */
    fun toResponse(execution: PipelineExecution): PipelineExecutionResponse =
        PipelineExecutionResponse(
            executionId = execution.executionId,
            pipelineId = execution.pipelineId,
            pipelineName = execution.pipelineName,
            status = execution.status,
            startedAt = execution.startedAt,
            message = execution.message,
        )

    // === 보안 고려사항: 필드 노출 제어 ===

    /**
     * 보안 수준에 따른 응답 DTO 변환 (Entity 기반)
     * 금융 프로젝트와 같이 보안이 중요한 환경에서 사용
     * FieldAccessControl을 활용하여 동적 필드 노출 제어
     * Note: jobCount must be provided separately since jobs relationship was removed.
     */
    fun toSecureResponse(
        entity: com.github.lambda.domain.model.pipeline.PipelineEntity,
        securityLevel: SecurityLevel? = null,
        jobCount: Int = 0,
    ): PipelineResponse {
        val actualSecurityLevel = securityLevel ?: fieldAccessControl.getCurrentUserSecurityLevel()

        return PipelineResponse(
            id = entity.id,
            name = entity.name,
            description = getSecureField(entity.description, "description", actualSecurityLevel),
            status = entity.status,
            owner = getSecureField(entity.owner, "owner", actualSecurityLevel, entity.owner),
            scheduleExpression =
                if (fieldAccessControl.canAccessField("scheduleExpression")) {
                    entity.scheduleExpression
                } else {
                    null
                },
            isActive = entity.isActive,
            jobCount = jobCount,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }

    /**
     * 보안 수준에 따른 응답 DTO 변환 (DTO 기반 - deprecated)
     */
    @Deprecated("Use toSecureResponse(entity: PipelineEntity, securityLevel: SecurityLevel?) instead")
    fun toSecureResponse(
        dto: PipelineDto,
        securityLevel: SecurityLevel? = null,
    ): PipelineResponse {
        val actualSecurityLevel = securityLevel ?: fieldAccessControl.getCurrentUserSecurityLevel()

        return PipelineResponse(
            id = dto.id,
            name = dto.name,
            description = getSecureField(dto.description, "description", actualSecurityLevel),
            status = dto.status,
            owner = getSecureField(dto.owner, "owner", actualSecurityLevel, dto.owner),
            scheduleExpression =
                if (fieldAccessControl.canAccessField("scheduleExpression")) {
                    dto.scheduleExpression
                } else {
                    null
                },
            isActive = dto.isActive,
            jobCount = dto.jobCount,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
        )
    }

    /**
     * 필드별 보안 정책을 적용하여 안전한 데이터 반환
     */
    private fun getSecureField(
        originalValue: String?,
        fieldName: String,
        securityLevel: SecurityLevel,
        targetUserId: String? = null,
    ): String? {
        if (originalValue == null) return null

        // 필드 접근 권한 확인
        if (!fieldAccessControl.canAccessField(fieldName, targetUserId)) {
            return null
        }

        // 마스킹 수준 결정 및 적용
        val maskingLevel = fieldAccessControl.getMaskingLevel(fieldName)
        return fieldAccessControl.maskData(originalValue, maskingLevel)
    }

    /**
     * 통계 도메인 객체를 응답 DTO로 변환 (보안 레벨 적용)
     */
    fun toStatisticsResponse(
        statistics: PipelineStatistics,
        securityLevel: SecurityLevel = SecurityLevel.INTERNAL,
    ): Map<String, Any> {
        val baseStats =
            mapOf(
                "totalPipelines" to statistics.totalPipelines,
                "activePipelines" to statistics.activePipelines,
                "runningPipelines" to statistics.runningPipelines,
                "pausedPipelines" to statistics.pausedPipelines,
                "failedPipelines" to statistics.failedPipelines,
                "totalJobs" to statistics.totalJobs,
                "averageJobsPerPipeline" to statistics.averageJobsPerPipeline,
                "statusBreakdown" to statistics.statusBreakdown,
            )

        return if (securityLevel == SecurityLevel.ADMIN) {
            baseStats + ("ownerBreakdown" to statistics.ownerBreakdown)
        } else {
            baseStats
        }
    }
}
