package com.github.lambda.infra.repository

import com.github.lambda.domain.model.metric.MetricEntity
import com.github.lambda.domain.repository.MetricRepositoryDsl
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

/**
 * Metric DSL Repository Implementation
 *
 * Implements complex queries for metrics using JPQL.
 */
@Repository("metricRepositoryDsl")
class MetricRepositoryDslImpl : MetricRepositoryDsl {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun findByFilters(
        tag: String?,
        owner: String?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<MetricEntity> {
        val queryBuilder = StringBuilder("SELECT DISTINCT m FROM MetricEntity m")
        val parameters = mutableMapOf<String, Any>()

        // Join with tags collection if filtering by tag
        if (tag != null) {
            queryBuilder.append(" JOIN m.tags t")
        }

        queryBuilder.append(" WHERE 1=1")

        // Filter by tag (exact match in tags collection)
        tag?.let {
            queryBuilder.append(" AND t = :tag")
            parameters["tag"] = it
        }

        // Filter by owner (partial match, case-insensitive)
        owner?.let {
            queryBuilder.append(" AND LOWER(m.owner) LIKE LOWER(:owner)")
            parameters["owner"] = "%$it%"
        }

        // Search in name and description (partial match, case-insensitive)
        search?.let { term ->
            queryBuilder.append(" AND (LOWER(m.name) LIKE LOWER(:search) OR LOWER(m.description) LIKE LOWER(:search))")
            parameters["search"] = "%$term%"
        }

        // Exclude soft-deleted entities
        queryBuilder.append(" AND m.deletedAt IS NULL")

        queryBuilder.append(" ORDER BY m.updatedAt DESC")

        val query = entityManager.createQuery(queryBuilder.toString(), MetricEntity::class.java)
        parameters.forEach { (key, value) -> query.setParameter(key, value) }

        return query
            .setFirstResult(offset)
            .setMaxResults(limit.coerceIn(1, 500))
            .resultList
    }

    override fun findByTagsIn(tags: List<String>): List<MetricEntity> {
        if (tags.isEmpty()) return emptyList()

        val jpql = """
            SELECT DISTINCT m FROM MetricEntity m
            JOIN m.tags t
            WHERE t IN :tags
            AND m.deletedAt IS NULL
            ORDER BY m.updatedAt DESC
        """

        return entityManager
            .createQuery(jpql, MetricEntity::class.java)
            .setParameter("tags", tags)
            .resultList
    }
}
