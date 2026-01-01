package com.github.lambda.infra.repository

import com.github.lambda.domain.model.catalog.CatalogFilters
import com.github.lambda.domain.model.catalog.CatalogTableEntity
import com.github.lambda.domain.repository.CatalogTableRepositoryDsl
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Catalog Table DSL Repository Implementation
 *
 * Handles complex filtering and search queries for CatalogTableEntity.
 * Uses JPA EntityManager for dynamic query construction.
 * Follows the same pattern as DatasetRepositoryDslImpl.
 */
@Repository
class CatalogTableRepositoryDslImpl : CatalogTableRepositoryDsl {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun findByFilters(
        filters: CatalogFilters,
        pageable: Pageable,
    ): Page<CatalogTableEntity> {
        val queryBuilder = StringBuilder("SELECT c FROM CatalogTableEntity c WHERE 1=1")
        val parameters = mutableMapOf<String, Any>()

        // Project filter (exact match)
        filters.project?.let {
            queryBuilder.append(" AND c.project = :project")
            parameters["project"] = it
        }

        // Dataset filter (exact match)
        filters.dataset?.let {
            queryBuilder.append(" AND c.datasetName = :dataset")
            parameters["dataset"] = it
        }

        // Owner filter (partial match)
        filters.owner?.let {
            queryBuilder.append(" AND LOWER(c.owner) LIKE LOWER(:owner)")
            parameters["owner"] = "%$it%"
        }

        // Team filter (exact match)
        filters.team?.let {
            queryBuilder.append(" AND c.team = :team")
            parameters["team"] = it
        }

        // Tags filter (AND condition - all tags must match)
        filters.tags.forEachIndexed { index, tag ->
            queryBuilder.append(" AND :tag$index MEMBER OF c.tags")
            parameters["tag$index"] = tag
        }

        queryBuilder.append(" ORDER BY c.lastUpdated DESC NULLS LAST")

        val query = entityManager.createQuery(queryBuilder.toString(), CatalogTableEntity::class.java)
        parameters.forEach { (key, value) -> query.setParameter(key, value) }

        val results =
            query
                .setFirstResult(filters.offset)
                .setMaxResults(filters.limit)
                .resultList

        val totalCount = countByFilters(filters)

        return PageImpl(results, pageable, totalCount)
    }

    override fun countByFilters(filters: CatalogFilters): Long {
        val queryBuilder = StringBuilder("SELECT COUNT(c) FROM CatalogTableEntity c WHERE 1=1")
        val parameters = mutableMapOf<String, Any>()

        filters.project?.let {
            queryBuilder.append(" AND c.project = :project")
            parameters["project"] = it
        }

        filters.dataset?.let {
            queryBuilder.append(" AND c.datasetName = :dataset")
            parameters["dataset"] = it
        }

        filters.owner?.let {
            queryBuilder.append(" AND LOWER(c.owner) LIKE LOWER(:owner)")
            parameters["owner"] = "%$it%"
        }

        filters.team?.let {
            queryBuilder.append(" AND c.team = :team")
            parameters["team"] = it
        }

        filters.tags.forEachIndexed { index, tag ->
            queryBuilder.append(" AND :tag$index MEMBER OF c.tags")
            parameters["tag$index"] = tag
        }

        val query = entityManager.createQuery(queryBuilder.toString(), Long::class.java)
        parameters.forEach { (key, value) -> query.setParameter(key, value) }

        return query.singleResult
    }

    override fun searchByKeyword(
        keyword: String,
        project: String?,
        limit: Int,
    ): List<CatalogTableEntity> {
        val queryBuilder =
            StringBuilder(
                """
            SELECT DISTINCT c FROM CatalogTableEntity c
            LEFT JOIN c.columns col
            WHERE (LOWER(c.name) LIKE LOWER(:keyword)
                   OR LOWER(c.description) LIKE LOWER(:keyword)
                   OR LOWER(col.name) LIKE LOWER(:keyword))
            """,
            )
        val parameters = mutableMapOf<String, Any>("keyword" to "%$keyword%")

        project?.let {
            queryBuilder.append(" AND c.project = :project")
            parameters["project"] = it
        }

        queryBuilder.append(" ORDER BY c.lastUpdated DESC NULLS LAST")

        val query = entityManager.createQuery(queryBuilder.toString(), CatalogTableEntity::class.java)
        parameters.forEach { (key, value) -> query.setParameter(key, value) }

        return query.setMaxResults(limit).resultList
    }

    override fun findStaleTables(
        thresholdHours: Int,
        limit: Int,
    ): List<CatalogTableEntity> {
        val cutoffTime = Instant.now().minusSeconds(thresholdHours.toLong() * 3600)

        val jpql =
            """
            SELECT c FROM CatalogTableEntity c
            WHERE c.lastUpdated IS NOT NULL
            AND c.lastUpdated < :cutoffTime
            ORDER BY c.lastUpdated ASC
            """

        return entityManager
            .createQuery(jpql, CatalogTableEntity::class.java)
            .setParameter("cutoffTime", cutoffTime)
            .setMaxResults(limit)
            .resultList
    }

    override fun getCatalogStatistics(project: String?): Map<String, Any> {
        val totalQuery =
            if (project != null) {
                "SELECT COUNT(c) FROM CatalogTableEntity c WHERE c.project = :project"
            } else {
                "SELECT COUNT(c) FROM CatalogTableEntity c"
            }

        val query = entityManager.createQuery(totalQuery, Long::class.java)
        if (project != null) {
            query.setParameter("project", project)
        }

        val totalTables = query.singleResult

        // Stale tables count
        val cutoffTime = Instant.now().minusSeconds(24 * 3600)
        val staleQuery =
            if (project != null) {
                """
                SELECT COUNT(c) FROM CatalogTableEntity c
                WHERE c.project = :project
                AND c.lastUpdated IS NOT NULL
                AND c.lastUpdated < :cutoffTime
                """
            } else {
                """
                SELECT COUNT(c) FROM CatalogTableEntity c
                WHERE c.lastUpdated IS NOT NULL
                AND c.lastUpdated < :cutoffTime
                """
            }

        val staleCountQuery = entityManager.createQuery(staleQuery, Long::class.java)
        staleCountQuery.setParameter("cutoffTime", cutoffTime)
        if (project != null) {
            staleCountQuery.setParameter("project", project)
        }

        val staleTables = staleCountQuery.singleResult

        // Average quality score
        val qualityQuery =
            if (project != null) {
                """
                SELECT AVG(c.qualityScore) FROM CatalogTableEntity c
                WHERE c.project = :project AND c.qualityScore IS NOT NULL
                """
            } else {
                """
                SELECT AVG(c.qualityScore) FROM CatalogTableEntity c
                WHERE c.qualityScore IS NOT NULL
                """
            }

        val qualityScoreQuery = entityManager.createQuery(qualityQuery, Double::class.java)
        if (project != null) {
            qualityScoreQuery.setParameter("project", project)
        }

        val avgQualityScore = qualityScoreQuery.singleResult ?: 0.0

        return mapOf(
            "totalTables" to totalTables,
            "staleTables" to staleTables,
            "averageQualityScore" to avgQualityScore,
        )
    }

    override fun getTableCountByProject(): List<Map<String, Any>> {
        val jpql =
            """
            SELECT c.project, COUNT(c) as tableCount
            FROM CatalogTableEntity c
            GROUP BY c.project
            ORDER BY COUNT(c) DESC
            """

        val results = entityManager.createQuery(jpql, Array<Any>::class.java).resultList

        return results.map { row ->
            mapOf(
                "project" to row[0],
                "tableCount" to row[1],
            )
        }
    }

    override fun getTableCountByOwner(): List<Map<String, Any>> {
        val jpql =
            """
            SELECT c.owner, COUNT(c) as tableCount
            FROM CatalogTableEntity c
            GROUP BY c.owner
            ORDER BY COUNT(c) DESC
            """

        val results = entityManager.createQuery(jpql, Array<Any>::class.java).resultList

        return results.map { row ->
            mapOf(
                "owner" to row[0],
                "tableCount" to row[1],
            )
        }
    }

    override fun getTableCountByTag(): List<Map<String, Any>> {
        val jpql =
            """
            SELECT tag, COUNT(DISTINCT c) as tableCount
            FROM CatalogTableEntity c JOIN c.tags tag
            GROUP BY tag
            ORDER BY COUNT(DISTINCT c) DESC
            """

        val results = entityManager.createQuery(jpql, Array<Any>::class.java).resultList

        return results.map { row ->
            mapOf(
                "tag" to row[0],
                "tableCount" to row[1],
            )
        }
    }

    override fun findRecentlyUpdatedTables(
        limit: Int,
        daysSince: Int,
    ): List<CatalogTableEntity> {
        val cutoffTime = Instant.now().minusSeconds(daysSince.toLong() * 24 * 3600)

        val jpql =
            """
            SELECT c FROM CatalogTableEntity c
            WHERE c.lastUpdated IS NOT NULL
            AND c.lastUpdated >= :cutoffTime
            ORDER BY c.lastUpdated DESC
            """

        return entityManager
            .createQuery(jpql, CatalogTableEntity::class.java)
            .setParameter("cutoffTime", cutoffTime)
            .setMaxResults(limit)
            .resultList
    }

    override fun findByQualityScoreRange(
        minScore: Int,
        maxScore: Int,
        pageable: Pageable,
    ): Page<CatalogTableEntity> {
        val jpql =
            """
            SELECT c FROM CatalogTableEntity c
            WHERE c.qualityScore IS NOT NULL
            AND c.qualityScore >= :minScore
            AND c.qualityScore <= :maxScore
            ORDER BY c.qualityScore DESC
            """

        val countJpql =
            """
            SELECT COUNT(c) FROM CatalogTableEntity c
            WHERE c.qualityScore IS NOT NULL
            AND c.qualityScore >= :minScore
            AND c.qualityScore <= :maxScore
            """

        val results =
            entityManager
                .createQuery(jpql, CatalogTableEntity::class.java)
                .setParameter("minScore", minScore)
                .setParameter("maxScore", maxScore)
                .setFirstResult(pageable.offset.toInt())
                .setMaxResults(pageable.pageSize)
                .resultList

        val totalCount =
            entityManager
                .createQuery(countJpql, Long::class.java)
                .setParameter("minScore", minScore)
                .setParameter("maxScore", maxScore)
                .singleResult

        return PageImpl(results, pageable, totalCount)
    }
}
