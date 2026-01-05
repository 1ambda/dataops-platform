package com.dataops.basecamp.domain.external.airflow

import com.dataops.basecamp.domain.internal.workflow.ScheduleInfo

/**
 * Airflow 클라이언트 인터페이스 (Domain Port)
 *
 * 외부 Airflow 시스템과의 통신을 위한 도메인 포트 인터페이스
 */
interface AirflowClient {
    /**
     * DAG 실행 트리거
     *
     * @param dagId DAG ID
     * @param runId 실행 ID
     * @param conf 실행 설정 (JSON 형태)
     * @return 생성된 DAG 실행 ID
     */
    fun triggerDAGRun(
        dagId: String,
        runId: String,
        conf: Map<String, Any> = emptyMap(),
    ): String

    /**
     * DAG 실행 상태 조회
     *
     * @param dagId DAG ID
     * @param runId 실행 ID
     * @return DAG 실행 상태 정보
     */
    fun getDAGRun(
        dagId: String,
        runId: String,
    ): AirflowDAGRunStatusResponse

    /**
     * DAG 실행 중단
     *
     * @param dagId DAG ID
     * @param runId 실행 ID
     * @return 중단 성공 여부
     */
    fun stopDAGRun(
        dagId: String,
        runId: String,
    ): Boolean

    /**
     * DAG 일시정지/재시작
     *
     * @param dagId DAG ID
     * @param isPaused 일시정지 여부 (true: 일시정지, false: 재시작)
     * @return 설정 성공 여부
     */
    fun pauseDAG(
        dagId: String,
        isPaused: Boolean,
    ): Boolean

    /**
     * DAG 생성
     *
     * @param datasetName 데이터셋 이름
     * @param schedule 스케줄 정보
     * @param s3Path S3 경로
     * @return 생성된 DAG ID
     */
    fun createDAG(
        datasetName: String,
        schedule: ScheduleInfo,
        s3Path: String,
    ): String

    /**
     * DAG 삭제
     *
     * @param dagId DAG ID
     * @return 삭제 성공 여부
     */
    fun deleteDAG(dagId: String): Boolean

    /**
     * DAG 상태 조회
     *
     * @param dagId DAG ID
     * @return DAG 상태 정보
     */
    fun getDagStatus(dagId: String): AirflowDagStatusResponse

    /**
     * Airflow 서버 연결 상태 확인
     *
     * @return 연결 가능 여부
     */
    fun isAvailable(): Boolean = true

    // ============ Phase 4: Backfill & Run Sync Methods ============

    /**
     * Backfill 생성 (Airflow 3 API)
     *
     * @param dagId DAG ID
     * @param fromDate 시작 날짜 (ISO 8601 형식)
     * @param toDate 종료 날짜 (ISO 8601 형식)
     * @return 생성된 Backfill 응답
     */
    fun createBackfill(
        dagId: String,
        fromDate: String,
        toDate: String,
    ): BackfillCreateResponse

    /**
     * Backfill 상태 조회
     *
     * @param backfillId Backfill ID
     * @return Backfill 상태 정보
     */
    fun getBackfillStatus(backfillId: String): BackfillStatusResponse

    /**
     * Backfill 일시정지
     *
     * @param backfillId Backfill ID
     * @return 일시정지된 Backfill 상태
     */
    fun pauseBackfill(backfillId: String): BackfillStatusResponse

    /**
     * Backfill 재개
     *
     * @param backfillId Backfill ID
     * @return 재개된 Backfill 상태
     */
    fun unpauseBackfill(backfillId: String): BackfillStatusResponse

    /**
     * Backfill 취소
     *
     * @param backfillId Backfill ID
     * @return 취소 성공 여부
     */
    fun cancelBackfill(backfillId: String): Boolean

    /**
     * 최근 DAG Run 목록 조회 (동기화용)
     *
     * @param since 조회 시작 시간
     * @param limit 최대 조회 개수
     * @return DAG Run 목록
     */
    fun listRecentDagRuns(
        since: java.time.LocalDateTime,
        limit: Int = 100,
    ): List<AirflowDagRunResponse>

    /**
     * 특정 DAG의 최근 Run 목록 조회
     *
     * @param dagId DAG ID
     * @param limit 최대 조회 개수
     * @return DAG Run 목록
     */
    fun listDagRuns(
        dagId: String,
        limit: Int = 25,
    ): List<AirflowDagRunResponse>

    /**
     * 특정 DAG Run의 Task Instance 목록 조회
     *
     * @param dagId DAG ID
     * @param dagRunId DAG Run ID
     * @return Task Instance 목록
     */
    fun getTaskInstances(
        dagId: String,
        dagRunId: String,
    ): List<AirflowTaskInstanceResponse>
}
