package com.github.lambda.infra.repository

import com.github.lambda.domain.entity.resource.QResourceEntity
import com.github.lambda.domain.entity.resource.ResourceEntity
import com.github.lambda.domain.repository.ResourceRepositoryDsl
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

/**
 * 리소스 리포지토리 DSL 구현체
 */
@Repository("resourceRepositoryDsl")
class ResourceRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : ResourceRepositoryDsl {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun searchResourcesWithComplexConditions(
        userId: Long?,
        nameKeyword: String?,
        page: Int,
        size: Int,
    ): List<ResourceEntity> {
        val queryBuilder = StringBuilder("SELECT r FROM ResourceEntity r WHERE r.deletedAt IS NULL")
        val parameters = mutableMapOf<String, Any>()

        userId?.let {
            queryBuilder.append(" AND r.userId = :userId")
            parameters["userId"] = it
        }

        nameKeyword?.takeIf { it.isNotBlank() }?.let {
            queryBuilder.append(" AND r.name LIKE :nameKeyword")
            parameters["nameKeyword"] = "%$it%"
        }

        queryBuilder.append(" ORDER BY r.createdAt DESC")

        val query = entityManager.createQuery(queryBuilder.toString(), ResourceEntity::class.java)
        parameters.forEach { (key, value) -> query.setParameter(key, value) }

        return query
            .setFirstResult(page * size)
            .setMaxResults(size)
            .resultList
    }

    override fun findResourcesUsingQueryDSL(
        nameContains: String?,
        limit: Int,
    ): List<ResourceEntity> {
        val qResource = QResourceEntity.resourceEntity

        return queryFactory
            .selectFrom(qResource)
            .where(
                qResource.deletedAt.isNull.and(
                    nameContains?.let {
                        qResource.resource.containsIgnoreCase(it)
                    },
                ),
            ).orderBy(qResource.createdAt.desc())
            .limit(limit.toLong())
            .fetch()
    }

    override fun countResourcesByUserUsingQueryDSL(): Map<Long, Long> {
        val qResource = QResourceEntity.resourceEntity

        return queryFactory
            .select(qResource.userId, qResource.count())
            .from(qResource)
            .where(qResource.deletedAt.isNull)
            .groupBy(qResource.userId)
            .fetch()
            .associate { tuple ->
                tuple.get(qResource.userId)!! to tuple.get(qResource.count())!!
            }
    }
}
