package com.github.lambda.infra.repository

import com.github.lambda.domain.entity.query.QueryExecutionEntity
import com.github.lambda.domain.model.query.QueryStatus
import com.github.lambda.domain.query.query.QueryScopeFilter
import com.github.lambda.domain.repository.QueryExecutionRepositoryDsl
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * QueryExecution DSL Repository Implementation
 *
 * Complex query implementation using JPA EntityManager for dynamic queries.
 * Handles filtering, scope-based access, and pagination for query execution history.
 */
@Repository
class QueryExecutionRepositoryDslImpl : QueryExecutionRepositoryDsl {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun findByFilter(
        submittedBy: String?,
        isSystemQuery: Boolean?,
        status: QueryStatus?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        limit: Int,
        offset: Int,
    ): List<QueryExecutionEntity> {
        val queryBuilder = StringBuilder("SELECT q FROM QueryExecutionEntity q WHERE 1=1")
        val parameters = mutableMapOf<String, Any>()

        // Filter by submittedBy
        submittedBy?.let {
            queryBuilder.append(" AND q.submittedBy = :submittedBy")
            parameters["submittedBy"] = it
        }

        // Filter by system query flag
        isSystemQuery?.let {
            queryBuilder.append(" AND q.isSystemQuery = :isSystemQuery")
            parameters["isSystemQuery"] = it
        }

        // Filter by status
        status?.let {
            queryBuilder.append(" AND q.status = :status")
            parameters["status"] = it
        }

        // Filter by date range
        startDate?.let {
            queryBuilder.append(" AND DATE(q.submittedAt) >= :startDate")
            parameters["startDate"] = it
        }

        endDate?.let {
            queryBuilder.append(" AND DATE(q.submittedAt) <= :endDate")
            parameters["endDate"] = it
        }

        queryBuilder.append(" ORDER BY q.submittedAt DESC")

        val query = entityManager.createQuery(queryBuilder.toString(), QueryExecutionEntity::class.java)
        parameters.forEach { (key, value) -> query.setParameter(key, value) }

        return query
            .setFirstResult(offset)
            .setMaxResults(limit)
            .resultList
    }

    override fun findByScope(
        filter: QueryScopeFilter,
        status: QueryStatus?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        limit: Int,
        offset: Int,
    ): List<QueryExecutionEntity> =
        findByFilter(
            submittedBy = filter.submittedBy,
            isSystemQuery = filter.isSystemQuery,
            status = status,
            startDate = startDate,
            endDate = endDate,
            limit = limit,
            offset = offset,
        )

    override fun countByFilter(
        submittedBy: String?,
        isSystemQuery: Boolean?,
        status: QueryStatus?,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): Long {
        val queryBuilder = StringBuilder("SELECT COUNT(q) FROM QueryExecutionEntity q WHERE 1=1")
        val parameters = mutableMapOf<String, Any>()

        submittedBy?.let {
            queryBuilder.append(" AND q.submittedBy = :submittedBy")
            parameters["submittedBy"] = it
        }

        isSystemQuery?.let {
            queryBuilder.append(" AND q.isSystemQuery = :isSystemQuery")
            parameters["isSystemQuery"] = it
        }

        status?.let {
            queryBuilder.append(" AND q.status = :status")
            parameters["status"] = it
        }

        startDate?.let {
            queryBuilder.append(" AND DATE(q.submittedAt) >= :startDate")
            parameters["startDate"] = it
        }

        endDate?.let {
            queryBuilder.append(" AND DATE(q.submittedAt) <= :endDate")
            parameters["endDate"] = it
        }

        val query = entityManager.createQuery(queryBuilder.toString(), Long::class.java)
        parameters.forEach { (key, value) -> query.setParameter(key, value) }

        return query.singleResult
    }

    override fun findRunningQueries(submittedBy: String?): List<QueryExecutionEntity> {
        val jpql =
            if (submittedBy != null) {
                """
            SELECT q FROM QueryExecutionEntity q
            WHERE q.status IN :runningStatuses
            AND q.submittedBy = :submittedBy
            ORDER BY q.submittedAt DESC
            """
            } else {
                """
            SELECT q FROM QueryExecutionEntity q
            WHERE q.status IN :runningStatuses
            ORDER BY q.submittedAt DESC
            """
            }

        val query = entityManager.createQuery(jpql, QueryExecutionEntity::class.java)
        query.setParameter("runningStatuses", listOf(QueryStatus.PENDING, QueryStatus.RUNNING))

        if (submittedBy != null) {
            query.setParameter("submittedBy", submittedBy)
        }

        return query.resultList
    }
}
