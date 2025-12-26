package com.github.lambda.domain.repository

import com.github.lambda.domain.model.resource.ResourceEntity
import java.util.*

/**
 * 리소스 Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * 기본 CRUD 작업과 도메인 특화 쿼리 작업을 정의합니다.
 * JpaRepository와 동일한 시그니처를 사용하여 충돌을 방지합니다.
 */
interface ResourceRepositoryJpa {
    // 기본 CRUD 작업 (JpaRepository와 다른 시그니처로 충돌 방지)
    fun save(resource: ResourceEntity): ResourceEntity

    // findById는 JpaRepository에서 제공하므로 제거
    fun deleteById(id: Long)

    fun existsById(id: Long): Boolean

    fun findAll(): List<ResourceEntity>

    // 도메인 특화 조회 메서드
    fun findByUserId(userId: Long): List<ResourceEntity>

    fun findByResource(resource: String): List<ResourceEntity>

    fun findByResourceContaining(keyword: String): List<ResourceEntity>

    // 대량 작업
    fun deleteBulkByUserId(userId: Long): Int

    // 페이지네이션 조회
    fun findByUserIdWithPagination(
        userId: Long,
        page: Int,
        size: Int,
    ): List<ResourceEntity>

    // 통계 및 집계
    fun countByUserId(userId: Long): Long

    fun getTotalResourcesCount(): Long
}
