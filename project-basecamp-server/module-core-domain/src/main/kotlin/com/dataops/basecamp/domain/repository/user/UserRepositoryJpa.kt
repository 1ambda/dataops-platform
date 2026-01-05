package com.dataops.basecamp.domain.repository.user

import com.dataops.basecamp.domain.entity.user.UserEntity
import java.util.*

/**
 * 사용자 Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * 기본 CRUD 작업과 도메인 특화 쿼리 작업을 정의합니다.
 * JpaRepository와 동일한 시그니처를 사용하여 충돌을 방지합니다.
 */
interface UserRepositoryJpa {
    // 기본 CRUD 작업 (JpaRepository와 다른 시그니처로 충돌 방지)
    fun save(user: UserEntity): UserEntity

    // findById는 JpaRepository에서 제공하므로 제거
    fun deleteById(id: Long)

    fun existsById(id: Long): Boolean

    fun findAll(): List<UserEntity>

    // 도메인 특화 조회 메서드
    fun findByEmail(email: String): UserEntity?

    fun findByUsername(username: String): UserEntity?

    fun existsByEmail(email: String): Boolean

    fun existsByUsername(username: String): Boolean

    // 페이지네이션 조회
    fun findAllWithPagination(
        page: Int,
        size: Int,
    ): List<UserEntity>

    fun findByActiveStatusWithPagination(
        isActive: Boolean,
        page: Int,
        size: Int,
    ): List<UserEntity>

    // 통계 및 집계
    fun countByActiveStatus(isActive: Boolean): Long

    fun countAll(): Long
}
