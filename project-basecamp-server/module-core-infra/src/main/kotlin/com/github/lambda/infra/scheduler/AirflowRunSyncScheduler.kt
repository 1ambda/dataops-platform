package com.github.lambda.infra.scheduler

import com.github.lambda.domain.service.AirflowRunSyncService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Airflow Run 동기화 스케줄러 (Phase 5)
 *
 * Airflow 클러스터에서 DAG Run 상태를 주기적으로 동기화합니다.
 * 기본적으로 비활성화되어 있으며, 설정으로 활성화 가능합니다.
 *
 * 설정:
 * - basecamp.workflow.run-sync.enabled: 스케줄러 활성화 여부 (기본: false)
 * - basecamp.workflow.run-sync.cron: 실행 주기 (기본: 0 *\/2 * * * * - 2분마다)
 * - basecamp.workflow.run-sync.lookback-hours: 동기화 대상 시간 범위 (기본: 24시간)
 * - basecamp.workflow.run-sync.batch-size: 클러스터당 최대 동기화 Run 수 (기본: 100)
 * - basecamp.workflow.run-sync.stale-threshold-hours: stale 동기화 기준 시간 (기본: 1시간)
 */
@Component
@ConditionalOnProperty(
    name = ["basecamp.workflow.run-sync.enabled"],
    havingValue = "true",
    matchIfMissing = false, // 기본값: 비활성화
)
class AirflowRunSyncScheduler(
    private val syncService: AirflowRunSyncService,
    @Value("\${basecamp.workflow.run-sync.lookback-hours:24}")
    private val lookbackHours: Long,
    @Value("\${basecamp.workflow.run-sync.batch-size:100}")
    private val batchSize: Int,
    @Value("\${basecamp.workflow.run-sync.stale-threshold-hours:1}")
    private val staleThresholdHours: Long,
) {
    private val log = LoggerFactory.getLogger(AirflowRunSyncScheduler::class.java)

    /**
     * 주기적인 Run 동기화 실행
     *
     * 모든 활성 Airflow 클러스터에서 DAG Run 상태를 동기화합니다.
     * 동기화 실패 시에도 다음 스케줄에 계속 실행됩니다.
     */
    @Scheduled(cron = "\${basecamp.workflow.run-sync.cron:0 */2 * * * *}")
    fun scheduledSync() {
        log.info(
            "Starting scheduled Airflow run sync (lookback: {}h, batch: {})",
            lookbackHours,
            batchSize,
        )

        try {
            val result = syncService.syncAllClusters(lookbackHours, batchSize)

            if (result.isSuccess) {
                log.info(
                    "Scheduled run sync completed successfully: clusters={}, updated={}, created={}",
                    result.totalClusters,
                    result.totalUpdated,
                    result.totalCreated,
                )
            } else {
                log.warn(
                    "Scheduled run sync completed with errors: clusters={}, updated={}, created={}, failed={}",
                    result.totalClusters,
                    result.totalUpdated,
                    result.totalCreated,
                    result.failedClusters,
                )

                // 실패한 클러스터 상세 로그
                result.clusterResults
                    .filter { it.error != null }
                    .forEach { cluster ->
                        log.warn(
                            "Cluster {} sync failed: {}",
                            cluster.clusterName,
                            cluster.error,
                        )
                    }
            }
        } catch (e: Exception) {
            log.error("Scheduled run sync failed unexpectedly", e)
            // 예외를 삼켜서 스케줄러가 계속 동작하도록 함
        }
    }

    /**
     * Stale Run 동기화 실행
     *
     * 동기화가 오래된 Run들을 찾아서 상태를 업데이트합니다.
     * 기본 스케줄링보다 낮은 빈도로 실행됩니다.
     */
    @Scheduled(cron = "\${basecamp.workflow.run-sync.stale-cron:0 0 */1 * * *}") // 기본: 1시간마다
    fun scheduledStaleSync() {
        log.info("Starting scheduled stale run sync (threshold: {}h)", staleThresholdHours)

        try {
            val result = syncService.syncStaleRuns(staleThresholdHours)

            if (result.isSuccess) {
                log.info(
                    "Scheduled stale sync completed successfully: updated={}",
                    result.totalUpdated,
                )
            } else {
                log.warn(
                    "Scheduled stale sync completed with errors: updated={}, failed={}",
                    result.totalUpdated,
                    result.failedClusters,
                )
            }
        } catch (e: Exception) {
            log.error("Scheduled stale sync failed unexpectedly", e)
            // 예외를 삼켜서 스케줄러가 계속 동작하도록 함
        }
    }
}
