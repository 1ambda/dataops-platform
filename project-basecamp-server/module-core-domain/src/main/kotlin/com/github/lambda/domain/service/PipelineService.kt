package com.github.lambda.domain.service

import com.github.lambda.domain.command.pipeline.*
import com.github.lambda.domain.model.pipeline.OwnerStatistics
import com.github.lambda.domain.model.pipeline.PipelineEntity
import com.github.lambda.domain.model.pipeline.PipelineExecution
import com.github.lambda.domain.model.pipeline.PipelineStatistics
import com.github.lambda.domain.model.pipeline.PipelineStatus
import com.github.lambda.domain.query.pipeline.*
import com.github.lambda.domain.repository.JobRepositoryDsl
import com.github.lambda.domain.repository.JobRepositoryJpa
import com.github.lambda.domain.repository.PipelineRepositoryDsl
import com.github.lambda.domain.repository.PipelineRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 파이프라인 서비스
 *
 * Pure Hexagonal Architecture 패턴을 적용한 도메인 서비스입니다.
 * - Services는 concrete classes (no interfaces)
 * - 명령과 조회가 명확히 분리됨
 * - Domain Entity를 직접 반환 (DTO 변환은 API layer에서 처리)
 * - 비즈니스 로직과 데이터 접근 로직 분리
 */
@Service
@Transactional(readOnly = true) // 기본값은 읽기 전용
class PipelineService(
    private val pipelineRepositoryJpa: PipelineRepositoryJpa,
    private val pipelineRepositoryDsl: PipelineRepositoryDsl,
    private val jobRepositoryJpa: JobRepositoryJpa,
    private val jobRepositoryDsl: JobRepositoryDsl,
) {
    // === 명령(Command) 처리 ===

    /**
     * 파이프라인 생성 명령 처리
     */
    @Transactional
    fun createPipeline(command: CreatePipelineCommand): PipelineEntity {
        val pipeline =
            PipelineEntity(
                name = command.name,
                description = command.description,
                owner = command.owner,
                scheduleExpression = command.scheduleExpression,
                isActive = command.isActive,
                status = PipelineStatus.DRAFT,
            )

        return pipelineRepositoryJpa.save(pipeline)
    }

    /**
     * 파이프라인 수정 명령 처리
     */
    @Transactional
    fun updatePipeline(command: UpdatePipelineCommand): PipelineEntity {
        val existing =
            pipelineRepositoryJpa.findById(command.id)
                ?: throw IllegalArgumentException("PipelineEntity not found: ${command.id}")

        existing.apply {
            name = command.name
            description = command.description
            scheduleExpression = command.scheduleExpression
        }

        return pipelineRepositoryJpa.save(existing)
    }

    /**
     * 파이프라인 상태 변경 명령 처리
     */
    @Transactional
    fun updatePipelineStatus(command: UpdatePipelineStatusCommand): PipelineEntity {
        val pipeline =
            pipelineRepositoryJpa.findById(command.id)
                ?: throw IllegalArgumentException("PipelineEntity not found: ${command.id}")

        pipeline.status = command.status

        return pipelineRepositoryJpa.save(pipeline)
    }

    /**
     * 파이프라인 활성화 토글 명령 처리
     */
    @Transactional
    fun togglePipelineActive(command: TogglePipelineActiveCommand): PipelineEntity {
        val pipeline =
            pipelineRepositoryJpa.findById(command.id)
                ?: throw IllegalArgumentException("PipelineEntity not found: ${command.id}")

        if (pipeline.isActive) {
            pipeline.deactivate()
        } else {
            pipeline.activate()
        }

        return pipelineRepositoryJpa.save(pipeline)
    }

    /**
     * 파이프라인 삭제 명령 처리 (소프트 삭제)
     */
    @Transactional
    fun deletePipeline(command: DeletePipelineCommand): Boolean =
        try {
            val pipeline =
                pipelineRepositoryJpa
                    .findById(command.id)
                    ?: throw IllegalArgumentException("PipelineEntity not found: ${command.id}")

            pipeline.deletedAt = LocalDateTime.now()
            pipelineRepositoryJpa.save(pipeline)
            true
        } catch (e: Exception) {
            false
        }

    /**
     * 파이프라인 실행 명령 처리
     */
    @Transactional
    fun executePipeline(command: ExecutePipelineCommand): PipelineExecution {
        val pipeline =
            pipelineRepositoryJpa.findById(command.id)
                ?: throw IllegalArgumentException("PipelineEntity not found: ${command.id}")

        require(pipeline.isExecutable()) {
            "PipelineEntity is not executable. Status: ${pipeline.status}, Active: ${pipeline.isActive}"
        }

        // 파이프라인 상태를 실행 중으로 변경
        pipeline.status = PipelineStatus.RUNNING
        pipelineRepositoryJpa.save(pipeline)

        // 실행 ID 생성
        val executionId = "exec_${System.currentTimeMillis()}_${pipeline.id}"

        // TODO: 실제 파이프라인 실행 로직 구현 (비동기)
        // - Job 스케줄링
        // - 실행 상태 추적
        // - 알림 시스템

        return PipelineExecution(
            executionId = executionId,
            pipelineId = pipeline.id!!,
            pipelineName = pipeline.name,
            status = "STARTED",
            startedAt = LocalDateTime.now(),
            message = "파이프라인 실행이 시작되었습니다.",
            parameters = command.parameters,
        )
    }

    /**
     * 파이프라인 실행 중지 명령 처리
     */
    @Transactional
    fun stopPipelineExecution(command: StopPipelineExecutionCommand): Boolean {
        val pipeline =
            pipelineRepositoryJpa.findById(command.pipelineId)
                ?: return false

        if (pipeline.status != PipelineStatus.RUNNING) {
            return false
        }

        pipeline.status = PipelineStatus.STOPPED
        pipelineRepositoryJpa.save(pipeline)

        // TODO: 실제 실행 중단 로직 구현
        // - 현재 실행 중인 Job 중단
        // - 리소스 정리
        // - 알림 발송

        return true
    }

    // === 조회(Query) 처리 ===

    /**
     * 파이프라인 단건 조회
     */
    fun getPipeline(query: GetPipelineQuery): PipelineEntity? =
        if (query.includeDeleted) {
            pipelineRepositoryJpa.findById(query.id)
        } else {
            pipelineRepositoryJpa.findByIdAndIsActiveTrue(query.id)
        }

    /**
     * 파이프라인 목록 조회 (조건부 검색)
     */
    fun getPipelines(query: GetPipelinesQuery): Page<PipelineEntity> =
        pipelineRepositoryDsl.searchPipelinesWithComplexConditions(
            owner = query.owner,
            status = query.status,
            isActive = query.isActive,
            pageable = query.pageable,
        )

    /**
     * 소유자별 파이프라인 조회
     */
    fun getPipelinesByOwner(query: GetPipelinesByOwnerQuery): Page<PipelineEntity> =
        if (query.includeInactive) {
            pipelineRepositoryJpa.findByOwnerOrderByUpdatedAtDesc(query.owner, query.pageable)
        } else {
            pipelineRepositoryJpa.findByOwnerAndIsActiveTrueOrderByUpdatedAtDesc(query.owner, query.pageable)
        }

    /**
     * 상태별 파이프라인 조회
     */
    fun getPipelinesByStatus(query: GetPipelinesByStatusQuery): Page<PipelineEntity> =
        if (query.includeInactive) {
            pipelineRepositoryJpa.findByStatusOrderByUpdatedAtDesc(query.status, query.pageable)
        } else {
            pipelineRepositoryJpa.findByStatusAndIsActiveTrueOrderByUpdatedAtDesc(query.status, query.pageable)
        }

    /**
     * 파이프라인 통계 조회
     */
    fun getPipelineStatistics(query: GetPipelineStatisticsQuery): PipelineStatistics {
        val stats = pipelineRepositoryDsl.getPipelineStatisticsWithJobCounts(query.owner)
        val ownerStats =
            if (query.owner == null) {
                pipelineRepositoryDsl.getPipelineCountByOwner().map { row ->
                    OwnerStatistics(
                        owner = row["owner"] as String,
                        pipelineCount = row["totalCount"] as Long,
                        activeCount = row["activeCount"] as Long,
                        inactiveCount = row["inactiveCount"] as Long,
                    )
                }
            } else {
                emptyList()
            }

        return PipelineStatistics(
            totalPipelines = stats["totalPipelines"] as Long,
            activePipelines = stats["activePipelines"] as Long,
            runningPipelines = stats["runningPipelines"] as Long,
            pausedPipelines = stats["pausedPipelines"] as Long,
            failedPipelines = stats["failedPipelines"] as Long,
            totalJobs = stats["totalJobs"] as Long,
            averageJobsPerPipeline = stats["averageJobsPerPipeline"] as Double,
            statusBreakdown = stats["statusBreakdown"] as? Map<PipelineStatus, Long> ?: emptyMap(),
            ownerBreakdown = ownerStats,
        )
    }
}
