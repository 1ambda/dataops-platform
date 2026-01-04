package com.github.lambda.domain.external

import com.github.lambda.domain.model.workflow.ScheduleInfo
import com.github.lambda.domain.model.workflow.WorkflowSourceType

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
    ): AirflowDAGRunStatus

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
    fun getDagStatus(dagId: String): AirflowDagStatus

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
    ): BackfillResponse

    /**
     * Backfill 상태 조회
     *
     * @param backfillId Backfill ID
     * @return Backfill 상태 정보
     */
    fun getBackfillStatus(backfillId: String): BackfillStatus

    /**
     * Backfill 일시정지
     *
     * @param backfillId Backfill ID
     * @return 일시정지된 Backfill 상태
     */
    fun pauseBackfill(backfillId: String): BackfillStatus

    /**
     * Backfill 재개
     *
     * @param backfillId Backfill ID
     * @return 재개된 Backfill 상태
     */
    fun unpauseBackfill(backfillId: String): BackfillStatus

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
    ): List<AirflowDagRun>

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
    ): List<AirflowDagRun>

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
    ): List<AirflowTaskInstance>
}

/**
 * Workflow 저장소 클라이언트 인터페이스 (Domain Port)
 *
 * 워크플로우 YAML 파일 저장/조회를 위한 도메인 포트 인터페이스
 */
interface WorkflowStorage {
    /**
     * 워크플로우 YAML 파일 저장
     *
     * @param datasetName 데이터셋 이름
     * @param sourceType 워크플로우 소스 타입 (MANUAL, CODE)
     * @param yamlContent YAML 파일 내용
     * @return 저장된 파일 경로 (s3Path 형식)
     */
    fun saveWorkflowYaml(
        datasetName: String,
        sourceType: WorkflowSourceType,
        yamlContent: String,
    ): String

    /**
     * 워크플로우 YAML 파일 조회
     *
     * @param s3Path 파일 경로
     * @return YAML 파일 내용
     */
    fun getWorkflowYaml(s3Path: String): String

    /**
     * 워크플로우 YAML 파일 삭제
     *
     * @param s3Path 파일 경로
     * @return 삭제 성공 여부
     */
    fun deleteWorkflowYaml(s3Path: String): Boolean

    /**
     * 워크플로우 YAML 파일 존재 여부 확인
     *
     * @param s3Path 파일 경로
     * @return 파일 존재 여부
     */
    fun existsWorkflowYaml(s3Path: String): Boolean

    /**
     * 특정 소스 타입의 모든 워크플로우 YAML 파일 목록 조회
     *
     * @param sourceType 워크플로우 소스 타입 (MANUAL, CODE)
     * @return 파일 경로 목록
     */
    fun listWorkflowYamls(sourceType: WorkflowSourceType): List<String>

    /**
     * 워크플로우 YAML 파일 수정
     *
     * @param s3Path 파일 경로
     * @param yamlContent 수정된 YAML 파일 내용
     * @return 수정된 파일 경로
     */
    fun updateWorkflowYaml(
        s3Path: String,
        yamlContent: String,
    ): String

    /**
     * 저장소의 모든 Spec 파일 경로 목록 조회 (S3 Sync용)
     *
     * @return 모든 YAML 파일 경로 목록
     */
    fun listAllSpecs(): List<String>

    /**
     * 특정 prefix 경로의 Spec 파일 목록 조회
     *
     * @param prefix 디렉토리 prefix (e.g., "workflows/manual/", "workflows/code/")
     * @return 해당 prefix 하위의 파일 경로 목록
     */
    fun listSpecsByPrefix(prefix: String): List<String>
}
