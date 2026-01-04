package com.github.lambda.domain.repository.airflow

import com.github.lambda.domain.entity.workflow.AirflowClusterEntity

/**
 * Airflow Cluster Repository DSL 인터페이스 (순수 도메인 추상화)
 *
 * Airflow 클러스터에 대한 복잡한 쿼리 작업을 정의합니다.
 * QueryDSL이나 기타 복잡한 쿼리 기술에 대한 추상화를 제공합니다.
 */
interface AirflowClusterRepositoryDsl {
    /**
     * Airflow URL로 클러스터 조회
     *
     * @param url Airflow URL
     * @return 해당 URL을 가진 클러스터 또는 null
     */
    fun findByAirflowUrl(url: String): AirflowClusterEntity?

    /**
     * 클러스터 이름으로 클러스터 목록 조회
     *
     * @param name 클러스터 이름 (부분 일치)
     * @return 해당 이름을 가진 클러스터 목록
     */
    fun findByClusterName(name: String): List<AirflowClusterEntity>

    /**
     * 클러스터 이름 패턴으로 검색 (대소문자 무시)
     *
     * @param namePattern 클러스터 이름 패턴
     * @return 패턴에 맞는 클러스터 목록
     */
    fun findByClusterNameContainingIgnoreCase(namePattern: String): List<AirflowClusterEntity>

    /**
     * DAG S3 경로로 클러스터 조회
     *
     * @param s3PathPattern S3 경로 패턴 (부분 일치)
     * @return 패턴에 맞는 클러스터 목록
     */
    fun findByDagS3PathContaining(s3PathPattern: String): List<AirflowClusterEntity>

    /**
     * DAG 이름 접두사로 클러스터 조회
     *
     * @param prefix DAG 이름 접두사
     * @return 해당 접두사를 가진 클러스터 또는 null
     */
    fun findByDagNamePrefix(prefix: String): AirflowClusterEntity?
}
