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
}
