package com.dataops.basecamp.infra.repository.airflow

import com.dataops.basecamp.common.enums.AirflowEnvironment
import com.dataops.basecamp.domain.entity.workflow.AirflowClusterEntity
import com.dataops.basecamp.domain.repository.airflow.AirflowClusterRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Airflow Cluster JPA Repository 구현 인터페이스
 *
 * Domain AirflowClusterRepositoryJpa 인터페이스와 JpaRepository를 모두 확장하는 Simplified Pattern입니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("airflowClusterRepositoryJpa")
interface AirflowClusterRepositoryJpaImpl :
    AirflowClusterRepositoryJpa,
    JpaRepository<AirflowClusterEntity, Long> {
    // 기본 조회 메서드들 (Spring Data JPA auto-implements)
    override fun findByTeam(team: String): AirflowClusterEntity?

    @Query("SELECT c FROM AirflowClusterEntity c WHERE c.isActive = true ORDER BY c.team")
    override fun findAllActive(): List<AirflowClusterEntity>

    // 환경별 조회
    override fun findByEnvironment(environment: AirflowEnvironment): List<AirflowClusterEntity>

    override fun findByEnvironmentAndIsActive(
        environment: AirflowEnvironment,
        isActive: Boolean,
    ): List<AirflowClusterEntity>

    // 존재 여부 확인
    override fun existsByTeam(team: String): Boolean

    @Query(
        """
        SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
        FROM AirflowClusterEntity c
        WHERE c.airflowUrl = :airflowUrl
        """,
    )
    override fun existsByAirflowUrl(
        @Param("airflowUrl") airflowUrl: String,
    ): Boolean

    // 집계
    override fun countByEnvironment(environment: AirflowEnvironment): Long

    override fun countByIsActive(isActive: Boolean): Long
}
