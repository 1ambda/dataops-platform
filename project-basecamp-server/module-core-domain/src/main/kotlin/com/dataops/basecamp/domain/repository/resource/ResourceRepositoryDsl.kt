package com.dataops.basecamp.domain.repository.resource

import com.dataops.basecamp.domain.entity.resource.ResourceEntity

/**
 * 리소스 Repository DSL 인터페이스 (순수 도메인 추상화)
 *
 * 복잡한 쿼리 및 집계 작업을 정의합니다.
 * QueryDSL이나 기타 복잡한 쿼리 기술에 대한 추상화를 제공합니다.
 */
interface ResourceRepositoryDsl {
    // 복합 검색 기능
    fun searchResourcesWithComplexConditions(
        userId: Long? = null,
        nameKeyword: String? = null,
        page: Int = 0,
        size: Int = 20,
    ): List<ResourceEntity>

    // QueryDSL을 사용한 타입-안전한 검색 기능 (테스트용)
    fun findResourcesUsingQueryDSL(
        nameContains: String? = null,
        limit: Int = 10,
    ): List<ResourceEntity>

    // QueryDSL을 사용한 집계 쿼리 (테스트용)
    fun countResourcesByUserUsingQueryDSL(): Map<Long, Long>
}
