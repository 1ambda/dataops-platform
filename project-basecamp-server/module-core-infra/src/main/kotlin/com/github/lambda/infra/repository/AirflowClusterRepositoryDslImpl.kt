package com.github.lambda.infra.repository

import com.github.lambda.domain.model.workflow.AirflowClusterEntity
import com.github.lambda.domain.model.workflow.QAirflowClusterEntity
import com.github.lambda.domain.repository.AirflowClusterRepositoryDsl
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

/**
 * Airflow Cluster Repository DSL 구현체
 *
 * QueryDSL을 사용하여 복잡한 쿼리 작업을 구현합니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("airflowClusterRepositoryDsl")
class AirflowClusterRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : AirflowClusterRepositoryDsl {
    private val cluster = QAirflowClusterEntity.airflowClusterEntity

    override fun findByAirflowUrl(url: String): AirflowClusterEntity? =
        queryFactory
            .selectFrom(cluster)
            .where(cluster.airflowUrl.eq(url))
            .fetchOne()

    override fun findByClusterName(name: String): List<AirflowClusterEntity> =
        queryFactory
            .selectFrom(cluster)
            .where(cluster.clusterName.eq(name))
            .orderBy(cluster.team.asc())
            .fetch()

    override fun findByClusterNameContainingIgnoreCase(namePattern: String): List<AirflowClusterEntity> =
        queryFactory
            .selectFrom(cluster)
            .where(cluster.clusterName.containsIgnoreCase(namePattern))
            .orderBy(cluster.team.asc())
            .fetch()

    override fun findByDagS3PathContaining(s3PathPattern: String): List<AirflowClusterEntity> =
        queryFactory
            .selectFrom(cluster)
            .where(cluster.dagS3Path.contains(s3PathPattern))
            .orderBy(cluster.team.asc())
            .fetch()

    override fun findByDagNamePrefix(prefix: String): AirflowClusterEntity? =
        queryFactory
            .selectFrom(cluster)
            .where(cluster.dagNamePrefix.eq(prefix))
            .fetchOne()
}
