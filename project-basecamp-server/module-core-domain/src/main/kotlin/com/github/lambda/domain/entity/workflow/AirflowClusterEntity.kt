package com.github.lambda.domain.entity.workflow

import com.github.lambda.domain.entity.BaseAuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * Airflow Cluster Entity
 *
 * 팀 기반 Airflow 클러스터 관리를 위한 엔티티입니다.
 * 각 팀은 하나의 Airflow 클러스터를 가질 수 있습니다.
 */
@Entity
@Table(
    name = "airflow_clusters",
    indexes = [
        Index(name = "idx_airflow_cluster_team", columnList = "team", unique = true),
        Index(name = "idx_airflow_cluster_env", columnList = "environment"),
        Index(name = "idx_airflow_cluster_active", columnList = "is_active"),
    ],
)
class AirflowClusterEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    /**
     * 팀 식별자 - 동일 환경 내 팀별 1개 클러스터 (UNIQUE)
     */
    @Column(nullable = false, length = 255, unique = true)
    val team: String,
    /**
     * 클러스터 이름
     */
    @Column(name = "cluster_name", nullable = false, length = 100)
    val clusterName: String,
    /**
     * Airflow 웹 UI 및 API URL
     */
    @Column(name = "airflow_url", nullable = false, length = 500)
    val airflowUrl: String,
    /**
     * 클러스터 환경 타입 (DEVELOPMENT / PRODUCTION)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val environment: AirflowEnvironment,
    /**
     * DAG 파일이 저장되는 S3 경로
     */
    @Column(name = "dag_s3_path", nullable = false, length = 500)
    val dagS3Path: String,
    /**
     * DAG 이름 접두사 (예: "team_a_")
     */
    @Column(name = "dag_name_prefix", nullable = false, length = 200)
    val dagNamePrefix: String,
    /**
     * 클러스터 활성화 여부
     */
    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,
    /**
     * Airflow 3 JWT Token (API Key) 인증
     */
    @Column(name = "api_key", nullable = false, length = 500)
    val apiKey: String,
    /**
     * 클러스터 설명
     */
    @Column(length = 1000)
    val description: String? = null,
) : BaseAuditableEntity() {
    /**
     * 클러스터가 활성 상태인지 확인
     */
    fun isActiveCluster(): Boolean = isActive

    /**
     * 개발 환경 클러스터인지 확인
     */
    fun isDevelopment(): Boolean = environment == AirflowEnvironment.DEVELOPMENT

    /**
     * 운영 환경 클러스터인지 확인
     */
    fun isProduction(): Boolean = environment == AirflowEnvironment.PRODUCTION

    /**
     * DAG ID 생성 (prefix + dagName)
     */
    fun generateDagId(dagName: String): String = "$dagNamePrefix$dagName"
}
