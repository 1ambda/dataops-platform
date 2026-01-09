package com.dataops.basecamp.infra.repository.project

import com.dataops.basecamp.domain.entity.project.ProjectEntity
import com.dataops.basecamp.domain.repository.project.ProjectRepositoryDsl
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

/**
 * Project DSL 리포지토리 구현체
 *
 * 복잡한 필터링과 검색 쿼리를 처리하는 구현체입니다.
 * JPA EntityManager를 사용하여 동적 쿼리를 구성합니다.
 */
@Repository
class ProjectRepositoryDslImpl : ProjectRepositoryDsl {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun findByFilters(
        search: String?,
        pageable: Pageable,
    ): Page<ProjectEntity> {
        val queryBuilder = StringBuilder("SELECT p FROM ProjectEntity p WHERE p.deletedAt IS NULL")
        val parameters = mutableMapOf<String, Any>()

        // 검색 필터 (이름 및 displayName에서 부분 일치)
        search?.let {
            queryBuilder.append(" AND (LOWER(p.name) LIKE LOWER(:search) OR LOWER(p.displayName) LIKE LOWER(:search))")
            parameters["search"] = "%$it%"
        }

        queryBuilder.append(" ORDER BY p.updatedAt DESC")

        val query = entityManager.createQuery(queryBuilder.toString(), ProjectEntity::class.java)
        parameters.forEach { (key, value) -> query.setParameter(key, value) }

        val results =
            query
                .setFirstResult(pageable.offset.toInt())
                .setMaxResults(pageable.pageSize)
                .resultList

        // Count query for total elements
        val totalCount = countByFilters(search)

        return PageImpl(results, pageable, totalCount)
    }

    override fun countByFilters(search: String?): Long {
        val queryBuilder = StringBuilder("SELECT COUNT(p) FROM ProjectEntity p WHERE p.deletedAt IS NULL")
        val parameters = mutableMapOf<String, Any>()

        search?.let {
            queryBuilder.append(" AND (LOWER(p.name) LIKE LOWER(:search) OR LOWER(p.displayName) LIKE LOWER(:search))")
            parameters["search"] = "%$it%"
        }

        val query = entityManager.createQuery(queryBuilder.toString(), Long::class.java)
        parameters.forEach { (key, value) -> query.setParameter(key, value) }

        return query.singleResult
    }
}
