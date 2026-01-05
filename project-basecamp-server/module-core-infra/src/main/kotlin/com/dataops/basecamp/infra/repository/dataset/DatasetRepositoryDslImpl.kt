package com.dataops.basecamp.infra.repository.dataset

import com.dataops.basecamp.domain.entity.dataset.DatasetEntity
import com.dataops.basecamp.domain.repository.dataset.DatasetRepositoryDsl
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * Dataset DSL 리포지토리 구현체
 *
 * 복잡한 필터링과 검색 쿼리를 처리하는 구현체입니다.
 * JPA EntityManager를 사용하여 동적 쿼리를 구성합니다.
 */
@Repository
class DatasetRepositoryDslImpl : DatasetRepositoryDsl {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun findByFilters(
        tag: String?,
        owner: String?,
        search: String?,
        pageable: Pageable,
    ): Page<DatasetEntity> {
        val queryBuilder = StringBuilder("SELECT d FROM DatasetEntity d WHERE 1=1")
        val parameters = mutableMapOf<String, Any>()

        // 소유자 필터 (부분 일치)
        owner?.let {
            queryBuilder.append(" AND LOWER(d.owner) LIKE LOWER(:owner)")
            parameters["owner"] = "%$it%"
        }

        // 태그 필터 (정확히 일치)
        tag?.let {
            queryBuilder.append(" AND :tag MEMBER OF d.tags")
            parameters["tag"] = it
        }

        // 검색 필터 (이름 및 설명에서 부분 일치)
        search?.let {
            queryBuilder.append(" AND (LOWER(d.name) LIKE LOWER(:search) OR LOWER(d.description) LIKE LOWER(:search))")
            parameters["search"] = "%$it%"
        }

        queryBuilder.append(" ORDER BY d.updatedAt DESC")

        val query = entityManager.createQuery(queryBuilder.toString(), DatasetEntity::class.java)
        parameters.forEach { (key, value) -> query.setParameter(key, value) }

        val results =
            query
                .setFirstResult(pageable.offset.toInt())
                .setMaxResults(pageable.pageSize)
                .resultList

        // Count query for total elements
        val totalCount = countByFilters(tag, owner, search)

        return PageImpl(results, pageable, totalCount)
    }

    override fun countByFilters(
        tag: String?,
        owner: String?,
        search: String?,
    ): Long {
        val queryBuilder = StringBuilder("SELECT COUNT(d) FROM DatasetEntity d WHERE 1=1")
        val parameters = mutableMapOf<String, Any>()

        owner?.let {
            queryBuilder.append(" AND LOWER(d.owner) LIKE LOWER(:owner)")
            parameters["owner"] = "%$it%"
        }

        tag?.let {
            queryBuilder.append(" AND :tag MEMBER OF d.tags")
            parameters["tag"] = it
        }

        search?.let {
            queryBuilder.append(" AND (LOWER(d.name) LIKE LOWER(:search) OR LOWER(d.description) LIKE LOWER(:search))")
            parameters["search"] = "%$it%"
        }

        val query = entityManager.createQuery(queryBuilder.toString(), Long::class.java)
        parameters.forEach { (key, value) -> query.setParameter(key, value) }

        return query.singleResult
    }

    override fun getDatasetStatistics(owner: String?): Map<String, Any> {
        val totalQuery =
            if (owner != null) {
                "SELECT COUNT(d) FROM DatasetEntity d WHERE d.owner = :owner"
            } else {
                "SELECT COUNT(d) FROM DatasetEntity d"
            }

        val query = entityManager.createQuery(totalQuery, Long::class.java)
        if (owner != null) {
            query.setParameter("owner", owner)
        }

        val totalDatasets = query.singleResult

        // 스케줄이 있는 dataset 개수
        val scheduledQuery =
            if (owner != null) {
                "SELECT COUNT(d) FROM DatasetEntity d WHERE d.owner = :owner AND d.scheduleCron IS NOT NULL"
            } else {
                "SELECT COUNT(d) FROM DatasetEntity d WHERE d.scheduleCron IS NOT NULL"
            }

        val scheduledCountQuery = entityManager.createQuery(scheduledQuery, Long::class.java)
        if (owner != null) {
            scheduledCountQuery.setParameter("owner", owner)
        }

        val scheduledDatasets = scheduledCountQuery.singleResult

        return mapOf(
            "totalDatasets" to totalDatasets,
            "scheduledDatasets" to scheduledDatasets,
            "averageTagsPerDataset" to 0.0, // 추후 구현
            "mostCommonTags" to emptyList<String>(),
        )
    }

    override fun getDatasetCountByOwner(): List<Map<String, Any>> {
        val jpql = """
            SELECT d.owner, COUNT(d) as totalCount,
                   COUNT(CASE WHEN d.scheduleCron IS NOT NULL THEN 1 END) as scheduledCount
            FROM DatasetEntity d
            GROUP BY d.owner
            ORDER BY COUNT(d) DESC
        """

        val results = entityManager.createQuery(jpql, Array<Any>::class.java).resultList

        return results.map { row ->
            mapOf(
                "owner" to row[0],
                "totalCount" to row[1],
                "scheduledCount" to row[2],
            )
        }
    }

    override fun getDatasetCountByTag(): List<Map<String, Any>> {
        val jpql = """
            SELECT tag, COUNT(DISTINCT d) as datasetCount
            FROM DatasetEntity d JOIN d.tags tag
            GROUP BY tag
            ORDER BY COUNT(DISTINCT d) DESC
        """

        val results = entityManager.createQuery(jpql, Array<Any>::class.java).resultList

        return results.map { row ->
            mapOf(
                "tag" to row[0],
                "datasetCount" to row[1],
            )
        }
    }

    override fun findRecentlyUpdatedDatasets(
        limit: Int,
        daysSince: Int,
    ): List<DatasetEntity> {
        val cutoffDate = LocalDateTime.now().minusDays(daysSince.toLong())

        val jpql = """
            SELECT d FROM DatasetEntity d
            WHERE d.updatedAt >= :cutoffDate
            ORDER BY d.updatedAt DESC
        """

        return entityManager
            .createQuery(jpql, DatasetEntity::class.java)
            .setParameter("cutoffDate", cutoffDate)
            .setMaxResults(limit)
            .resultList
    }

    override fun findDatasetsByDependency(dependency: String): List<DatasetEntity> {
        val jpql = """
            SELECT d FROM DatasetEntity d
            WHERE :dependency MEMBER OF d.dependencies
            ORDER BY d.name ASC
        """

        return entityManager
            .createQuery(jpql, DatasetEntity::class.java)
            .setParameter("dependency", dependency)
            .resultList
    }

    override fun findScheduledDatasets(cronPattern: String?): List<DatasetEntity> {
        val jpql =
            if (cronPattern != null) {
                """
                SELECT d FROM DatasetEntity d
                WHERE d.scheduleCron IS NOT NULL
                AND d.scheduleCron LIKE :cronPattern
                ORDER BY d.scheduleCron ASC, d.name ASC
            """
            } else {
                """
                SELECT d FROM DatasetEntity d
                WHERE d.scheduleCron IS NOT NULL
                ORDER BY d.scheduleCron ASC, d.name ASC
            """
            }

        val query = entityManager.createQuery(jpql, DatasetEntity::class.java)
        if (cronPattern != null) {
            query.setParameter("cronPattern", "%$cronPattern%")
        }

        return query.resultList
    }
}
