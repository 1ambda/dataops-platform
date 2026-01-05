package com.dataops.basecamp.infra.repository.resource

import com.dataops.basecamp.domain.entity.resource.ResourceEntity
import com.dataops.basecamp.domain.repository.resource.ResourceRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

/**
 * 리소스 JPA Repository 구현 인터페이스
 *
 * Domain ResourceRepositoryJpa 인터페이스를 구현하고 Spring Data JPA를 직접 상속합니다.
 * ResourceRepositoryJpaSpringData와 ResourceRepositoryJpaImpl을 하나의 인터페이스로 통합했습니다.
 */
@Repository("resourceRepositoryJpa")
interface ResourceRepositoryJpaImpl :
    ResourceRepositoryJpa,
    JpaRepository<ResourceEntity, Long> {
    // Spring Data JPA 커스텀 쿼리 메서드들 (기존 SpringData에서 이동)
    // 사용자별 조회
    override fun findByUserId(userId: Long): List<ResourceEntity>

    fun findByUserIdOrderByCreatedAtDesc(
        userId: Long,
        pageable: Pageable,
    ): Page<ResourceEntity>

    // 리소스명별 조회
    override fun findByResource(resource: String): List<ResourceEntity>

    override fun findByResourceContaining(keyword: String): List<ResourceEntity>

    // 대량 작업
    @Modifying
    @Query("DELETE FROM ResourceEntity r WHERE r.userId = :userId")
    override fun deleteBulkByUserId(
        @Param("userId") userId: Long,
    ): Int

    // 통계 및 집계
    override fun countByUserId(userId: Long): Long

    @Query("SELECT COUNT(r) FROM ResourceEntity r")
    override fun getTotalResourcesCount(): Long

    // ResourceRepositoryJpa 인터페이스의 도메인 특화 메서드들을 구현

    // findById는 JpaRepository에서 제공되므로 별도 구현 불필요

    override fun findByUserIdWithPagination(
        userId: Long,
        page: Int,
        size: Int,
    ): List<ResourceEntity> = findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)).content
}
