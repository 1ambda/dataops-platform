package com.github.lambda.domain.repository.airflow

import com.github.lambda.common.enums.AirflowEnvironment
import com.github.lambda.domain.entity.workflow.AirflowClusterEntity

/**
 * Airflow Cluster Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * Airflow 클러스터에 대한 기본 CRUD 작업을 정의합니다.
 * JpaRepository와 동일한 시그니처를 사용하여 충돌을 방지합니다.
 */
interface AirflowClusterRepositoryJpa {
    // 기본 CRUD 작업
    fun save(cluster: AirflowClusterEntity): AirflowClusterEntity

    fun findById(id: Long): AirflowClusterEntity?

    fun findByTeam(team: String): AirflowClusterEntity?

    fun findAllActive(): List<AirflowClusterEntity>

    fun deleteById(id: Long)

    // 환경별 조회
    fun findByEnvironment(environment: AirflowEnvironment): List<AirflowClusterEntity>

    fun findByEnvironmentAndIsActive(
        environment: AirflowEnvironment,
        isActive: Boolean,
    ): List<AirflowClusterEntity>

    // 존재 여부 확인
    fun existsByTeam(team: String): Boolean

    fun existsByAirflowUrl(airflowUrl: String): Boolean

    // 전체 조회
    fun findAll(): List<AirflowClusterEntity>

    // 집계
    fun count(): Long

    fun countByEnvironment(environment: AirflowEnvironment): Long

    fun countByIsActive(isActive: Boolean): Long
}
