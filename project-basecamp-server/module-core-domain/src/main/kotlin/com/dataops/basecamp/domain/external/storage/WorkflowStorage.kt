package com.dataops.basecamp.domain.external.storage

import com.dataops.basecamp.common.enums.WorkflowSourceType

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
