package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.SpecSyncErrorType
import com.dataops.basecamp.common.enums.WorkflowSourceType
import com.dataops.basecamp.common.enums.WorkflowStatus
import com.dataops.basecamp.common.exception.SpecParseException
import com.dataops.basecamp.common.exception.SpecValidationException
import com.dataops.basecamp.domain.command.workflow.WorkflowSpec
import com.dataops.basecamp.domain.external.storage.WorkflowStorage
import com.dataops.basecamp.domain.internal.workflow.ScheduleInfo
import com.dataops.basecamp.domain.projection.workflow.SpecSyncErrorProjection
import com.dataops.basecamp.domain.projection.workflow.SpecSyncProjection
import com.dataops.basecamp.domain.repository.workflow.WorkflowRepositoryJpa
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * S3 Workflow Spec 동기화 서비스
 *
 * S3에 저장된 YAML Spec 파일을 주기적으로 읽어와 Workflow 엔티티로 동기화합니다.
 * S3 우선 정책을 따르므로, 기존 Workflow가 있으면 덮어씁니다.
 */
@Service
@Transactional(readOnly = true)
class WorkflowSpecSyncService(
    private val workflowStorage: WorkflowStorage,
    private val workflowRepositoryJpa: WorkflowRepositoryJpa,
    private val yamlParser: WorkflowYamlParser,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(WorkflowSpecSyncService::class.java)

    /**
     * S3에서 모든 YAML Spec을 가져와 Workflow로 동기화
     *
     * S3 우선 정책:
     * - 신규 Spec: 새 Workflow 생성
     * - 기존 Spec: 덮어쓰기 (S3 내용이 우선)
     *
     * @return 동기화 결과 (처리 통계 및 오류 목록)
     */
    @Transactional
    fun syncFromStorage(): SpecSyncProjection {
        val syncedAt = Instant.now(clock)
        log.info("Starting workflow spec sync from storage at {}", syncedAt)

        val specPaths =
            try {
                workflowStorage.listAllSpecs()
            } catch (e: Exception) {
                log.error("Failed to list specs from storage", e)
                return SpecSyncProjection(
                    totalProcessed = 0,
                    created = 0,
                    updated = 0,
                    failed = 1,
                    errors =
                        listOf(
                            SpecSyncErrorProjection(
                                specPath = "*",
                                message = "Failed to list specs: ${e.message}",
                                errorType = SpecSyncErrorType.STORAGE_ERROR,
                            ),
                        ),
                    syncedAt = syncedAt,
                )
            }

        if (specPaths.isEmpty()) {
            log.info("No specs found in storage, nothing to sync")
            return SpecSyncProjection.empty(syncedAt)
        }

        log.info("Found {} specs to sync", specPaths.size)

        var created = 0
        var updated = 0
        val errors = mutableListOf<SpecSyncErrorProjection>()

        for (specPath in specPaths) {
            try {
                val syncResult = syncSingleSpec(specPath)
                when (syncResult) {
                    SyncAction.CREATED -> created++
                    SyncAction.UPDATED -> updated++
                }
            } catch (e: Exception) {
                log.warn("Failed to sync spec: {}", specPath, e)
                errors.add(
                    SpecSyncErrorProjection(
                        specPath = specPath,
                        message = e.message ?: "Unknown error",
                        errorType = classifyError(e),
                    ),
                )
            }
        }

        val result =
            SpecSyncProjection(
                totalProcessed = specPaths.size,
                created = created,
                updated = updated,
                failed = errors.size,
                errors = errors,
                syncedAt = syncedAt,
            )

        log.info("Completed spec sync: {}", result.summary())
        return result
    }

    /**
     * 단일 Spec 파일 동기화
     *
     * @param specPath S3 경로
     * @return 수행된 작업 (생성/업데이트)
     */
    private fun syncSingleSpec(specPath: String): SyncAction {
        log.debug("Syncing spec: {}", specPath)

        // 1. YAML 내용 가져오기
        val yamlContent = workflowStorage.getWorkflowYaml(specPath)

        // 2. YAML 파싱 및 유효성 검사
        val parseResult = yamlParser.parse(yamlContent)
        if (!parseResult.isSuccess()) {
            throw SpecParseException(
                specContent = yamlContent,
                parseError = "Failed to parse YAML: ${parseResult.errorsOrEmpty().joinToString(", ")}",
            )
        }

        val spec =
            parseResult.getOrNull()
                ?: throw SpecParseException(
                    specContent = yamlContent,
                    parseError = "Parsed spec is null",
                )

        // 3. 기존 Workflow 확인
        val existingWorkflow = workflowRepositoryJpa.findByDatasetName(spec.name)

        return if (existingWorkflow != null) {
            // 4a. 기존 Workflow 업데이트 (S3 우선 정책)
            updateWorkflowFromSpec(spec, specPath)
            log.debug("Updated existing workflow: {}", spec.name)
            SyncAction.UPDATED
        } else {
            // 4b. 새 Workflow 생성
            createWorkflowFromSpec(spec, specPath)
            log.debug("Created new workflow: {}", spec.name)
            SyncAction.CREATED
        }
    }

    /**
     * Spec에서 새 Workflow 생성
     */
    private fun createWorkflowFromSpec(
        spec: WorkflowSpec,
        s3Path: String,
    ) {
        val entity =
            spec.toEntity(
                sourceType = WorkflowSourceType.CODE,
                s3Path = s3Path,
                airflowDagId = generateDagId(spec.name),
            )

        workflowRepositoryJpa.save(entity)
    }

    /**
     * 기존 Workflow를 Spec으로 업데이트 (S3 우선 정책)
     */
    private fun updateWorkflowFromSpec(
        spec: WorkflowSpec,
        s3Path: String,
    ) {
        val existingWorkflow =
            workflowRepositoryJpa.findByDatasetName(spec.name)
                ?: throw IllegalStateException("Workflow not found for update: ${spec.name}")

        // S3 우선 정책: Spec 내용으로 덮어쓰기 (WorkflowEntity는 var 필드 사용)
        existingWorkflow.apply {
            owner = spec.owner
            team = spec.team
            description = spec.description
            this.s3Path = s3Path
            sourceType = WorkflowSourceType.CODE
            // 비활성 상태는 유지, 그 외는 활성화
            if (status != WorkflowStatus.DISABLED) {
                status = WorkflowStatus.ACTIVE
            }
            schedule =
                ScheduleInfo(
                    cron = spec.schedule?.cron,
                    timezone = spec.schedule?.timezone ?: "UTC",
                )
        }

        workflowRepositoryJpa.save(existingWorkflow)
    }

    /**
     * DAG ID 생성 (WorkflowService와 동일한 로직)
     */
    private fun generateDagId(datasetName: String): String {
        val sanitized =
            datasetName
                .replace(".", "_")
                .replace("-", "_")
                .lowercase()
        return "dag_$sanitized"
    }

    /**
     * 예외를 에러 타입으로 분류
     */
    private fun classifyError(e: Exception): SpecSyncErrorType =
        when (e) {
            is SpecParseException -> SpecSyncErrorType.PARSE_ERROR
            is SpecValidationException -> SpecSyncErrorType.VALIDATION_ERROR
            is IllegalStateException -> SpecSyncErrorType.STORAGE_ERROR
            else -> SpecSyncErrorType.UNKNOWN
        }

    /**
     * 동기화 작업 유형
     */
    private enum class SyncAction {
        CREATED,
        UPDATED,
    }
}
