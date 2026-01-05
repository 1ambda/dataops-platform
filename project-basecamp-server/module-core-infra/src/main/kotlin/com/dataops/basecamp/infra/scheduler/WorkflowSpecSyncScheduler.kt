package com.dataops.basecamp.infra.scheduler

import com.dataops.basecamp.domain.service.WorkflowSpecSyncService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Workflow Spec 동기화 스케줄러
 *
 * S3에 저장된 YAML Spec 파일을 주기적으로 읽어와 Workflow 엔티티로 동기화합니다.
 * 기본적으로 5분마다 실행되며, 설정으로 비활성화 가능합니다.
 *
 * 설정:
 * - basecamp.workflow.spec-sync.enabled: 스케줄러 활성화 여부 (기본: true)
 * - basecamp.workflow.spec-sync.cron: 실행 주기 (기본: 0 *\/5 * * * * - 5분마다)
 */
@Component
@ConditionalOnProperty(
    name = ["basecamp.workflow.spec-sync.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class WorkflowSpecSyncScheduler(
    private val syncService: WorkflowSpecSyncService,
) {
    private val log = LoggerFactory.getLogger(WorkflowSpecSyncScheduler::class.java)

    /**
     * 주기적인 Spec 동기화 실행
     *
     * 동기화 실패 시에도 다음 스케줄에 계속 실행됩니다.
     */
    @Scheduled(cron = "\${basecamp.workflow.spec-sync.cron:0 */5 * * * *}")
    fun scheduledSync() {
        log.info("Starting scheduled workflow spec sync")

        try {
            val result = syncService.syncFromStorage()

            if (result.isSuccess()) {
                log.info(
                    "Scheduled sync completed successfully: created={}, updated={}, total={}",
                    result.created,
                    result.updated,
                    result.totalProcessed,
                )
            } else {
                log.warn(
                    "Scheduled sync completed with errors: created={}, updated={}, failed={}, errors={}",
                    result.created,
                    result.updated,
                    result.failed,
                    result.errors.map { it.toString() },
                )
            }
        } catch (e: Exception) {
            log.error("Scheduled sync failed unexpectedly", e)
            // 예외를 삼켜서 스케줄러가 계속 동작하도록 함
        }
    }
}
