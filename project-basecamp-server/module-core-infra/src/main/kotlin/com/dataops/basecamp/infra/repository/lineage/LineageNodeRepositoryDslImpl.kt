package com.dataops.basecamp.infra.repository.lineage

import com.dataops.basecamp.common.enums.LineageNodeType
import com.dataops.basecamp.domain.entity.lineage.LineageNodeEntity
import com.dataops.basecamp.domain.repository.lineage.LineageNodeRepositoryDsl
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

/**
 * Lineage Node DSL Repository Implementation
 *
 * Implements complex queries for lineage nodes using JPQL.
 */
@Repository("lineageNodeRepositoryDsl")
class LineageNodeRepositoryDslImpl : LineageNodeRepositoryDsl {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun findByFilters(
        type: LineageNodeType?,
        owner: String?,
        team: String?,
        tag: String?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<LineageNodeEntity> {
        val queryBuilder = StringBuilder("SELECT DISTINCT n FROM LineageNodeEntity n")
        val parameters = mutableMapOf<String, Any>()

        // Join with tags collection if filtering by tag
        if (tag != null) {
            queryBuilder.append(" JOIN n.tags t")
        }

        queryBuilder.append(" WHERE 1=1")

        // Filter by type
        type?.let {
            queryBuilder.append(" AND n.type = :type")
            parameters["type"] = it
        }

        // Filter by owner (partial match, case-insensitive)
        owner?.let {
            queryBuilder.append(" AND LOWER(n.owner) LIKE LOWER(:owner)")
            parameters["owner"] = "%$it%"
        }

        // Filter by team (partial match, case-insensitive)
        team?.let {
            queryBuilder.append(" AND LOWER(n.team) LIKE LOWER(:team)")
            parameters["team"] = "%$it%"
        }

        // Filter by tag (exact match in tags collection)
        tag?.let {
            queryBuilder.append(" AND t = :tag")
            parameters["tag"] = it
        }

        // Search in name and description (partial match, case-insensitive)
        search?.let { term ->
            queryBuilder.append(" AND (LOWER(n.name) LIKE LOWER(:search) OR LOWER(n.description) LIKE LOWER(:search))")
            parameters["search"] = "%$term%"
        }

        queryBuilder.append(" ORDER BY n.name ASC")

        val query = entityManager.createQuery(queryBuilder.toString(), LineageNodeEntity::class.java)
        parameters.forEach { (key, value) -> query.setParameter(key, value) }

        return query
            .setFirstResult(offset)
            .setMaxResults(limit.coerceIn(1, 500))
            .resultList
    }

    override fun findByNamesIn(names: List<String>): List<LineageNodeEntity> {
        if (names.isEmpty()) return emptyList()

        val jpql = """
            SELECT n FROM LineageNodeEntity n
            WHERE n.name IN :names
            ORDER BY n.name ASC
        """

        return entityManager
            .createQuery(jpql, LineageNodeEntity::class.java)
            .setParameter("names", names)
            .resultList
    }

    override fun findByTagsIn(tags: List<String>): List<LineageNodeEntity> {
        if (tags.isEmpty()) return emptyList()

        val jpql = """
            SELECT DISTINCT n FROM LineageNodeEntity n
            JOIN n.tags t
            WHERE t IN :tags
            ORDER BY n.name ASC
        """

        return entityManager
            .createQuery(jpql, LineageNodeEntity::class.java)
            .setParameter("tags", tags)
            .resultList
    }

    override fun findByNameContaining(namePattern: String): List<LineageNodeEntity> {
        val jpql = """
            SELECT n FROM LineageNodeEntity n
            WHERE LOWER(n.name) LIKE LOWER(:pattern)
            ORDER BY n.name ASC
        """

        return entityManager
            .createQuery(jpql, LineageNodeEntity::class.java)
            .setParameter("pattern", "%$namePattern%")
            .resultList
    }
}
