package com.github.lambda.infra.repository

import com.github.lambda.domain.model.pipeline.PipelineEntity
import com.github.lambda.domain.model.pipeline.PipelineStatus
import com.github.lambda.domain.repository.PipelineRepositoryDsl
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * Pipeline DSL 리포지토리 구현체
 */
@Repository
class PipelineRepositoryDslImpl : PipelineRepositoryDsl {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun searchPipelinesWithComplexConditions(
        owner: String?,
        status: PipelineStatus?,
        isActive: Boolean?,
        pageable: Pageable,
    ): Page<PipelineEntity> {
        val queryBuilder = StringBuilder("SELECT p FROM PipelineEntity p WHERE 1=1")
        val parameters = mutableMapOf<String, Any>()

        owner?.let {
            queryBuilder.append(" AND p.owner = :owner")
            parameters["owner"] = it
        }

        status?.let {
            queryBuilder.append(" AND p.status = :status")
            parameters["status"] = it
        }

        isActive?.let {
            queryBuilder.append(" AND p.isActive = :isActive")
            parameters["isActive"] = it
        }

        queryBuilder.append(" ORDER BY p.updatedAt DESC")

        val query = entityManager.createQuery(queryBuilder.toString(), PipelineEntity::class.java)
        parameters.forEach { (key, value) -> query.setParameter(key, value) }

        val results =
            query
                .setFirstResult(pageable.offset.toInt())
                .setMaxResults(pageable.pageSize)
                .resultList

        // Count query
        val countBuilder = StringBuilder("SELECT COUNT(p) FROM PipelineEntity p WHERE 1=1")
        owner?.let { countBuilder.append(" AND p.owner = :owner") }
        status?.let { countBuilder.append(" AND p.status = :status") }
        isActive?.let { countBuilder.append(" AND p.isActive = :isActive") }

        val countQuery = entityManager.createQuery(countBuilder.toString(), Long::class.java)
        parameters.forEach { (key, value) -> countQuery.setParameter(key, value) }
        val total = countQuery.singleResult

        return PageImpl(results, pageable, total)
    }

    override fun findRecentlyActivePipelines(
        limit: Int,
        daysSince: Int,
    ): List<PipelineEntity> {
        val cutoffDate = LocalDateTime.now().minusDays(daysSince.toLong())

        val jpql = """
            SELECT p FROM PipelineEntity p
            WHERE p.isActive = true AND p.updatedAt >= :cutoffDate
            ORDER BY p.updatedAt DESC
        """

        return entityManager
            .createQuery(jpql, PipelineEntity::class.java)
            .setParameter("cutoffDate", cutoffDate)
            .setMaxResults(limit)
            .resultList
    }

    override fun findPipelinesBySchedulePattern(
        schedulePattern: String,
        statusList: List<PipelineStatus>,
        isActive: Boolean,
    ): List<PipelineEntity> {
        val jpql = """
            SELECT p FROM PipelineEntity p
            WHERE p.scheduleExpression LIKE :schedulePattern
            AND p.status IN :statusList
            AND p.isActive = :isActive
            ORDER BY p.name ASC
        """

        return entityManager
            .createQuery(jpql, PipelineEntity::class.java)
            .setParameter("schedulePattern", "%$schedulePattern%")
            .setParameter("statusList", statusList)
            .setParameter("isActive", isActive)
            .resultList
    }

    override fun getPipelineStatisticsWithJobCounts(owner: String?): Map<String, Any> {
        val jpql =
            if (owner != null) {
                "SELECT COUNT(p) FROM PipelineEntity p WHERE p.owner = :owner"
            } else {
                "SELECT COUNT(p) FROM PipelineEntity p"
            }

        val query = entityManager.createQuery(jpql, Long::class.java)
        if (owner != null) {
            query.setParameter("owner", owner)
        }

        val totalPipelines = query.singleResult

        return mapOf(
            "totalPipelines" to totalPipelines,
            "activePipelines" to 0L,
            "totalJobs" to 0L,
            "successfulJobs" to 0L,
            "failedJobs" to 0L,
        )
    }

    override fun getPipelineCountByOwner(): List<Map<String, Any>> {
        val jpql = """
            SELECT p.owner, COUNT(p)
            FROM PipelineEntity p
            GROUP BY p.owner
            ORDER BY COUNT(p) DESC
        """

        val results = entityManager.createQuery(jpql, Array<Any>::class.java).resultList

        return results.map { row ->
            mapOf(
                "owner" to row[0],
                "pipelineCount" to row[1],
                "activePipelineCount" to 0L,
            )
        }
    }
}
