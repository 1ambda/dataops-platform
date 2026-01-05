package com.dataops.basecamp.domain.repository.user

import com.dataops.basecamp.domain.entity.user.UserAuthorityEntity
import java.util.*

/**
 * 사용자 권한 Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * 기본 CRUD 작업과 도메인 특화 쿼리 작업을 정의합니다.
 * JpaRepository와 동일한 시그니처를 사용하여 충돌을 방지합니다.
 */
interface UserAuthorityRepositoryJpa {
    // 기본 CRUD 작업 (JpaRepository와 다른 시그니처로 충돌 방지)
    fun save(userAuthority: UserAuthorityEntity): UserAuthorityEntity

    // findById는 JpaRepository에서 제공하므로 제거
    fun deleteById(id: Long)

    fun existsById(id: Long): Boolean

    fun findAll(): List<UserAuthorityEntity>

    // 도메인 특화 조회 메서드
    fun findByUserId(userId: Long): List<UserAuthorityEntity>

    fun findByAuthority(authority: String): List<UserAuthorityEntity>

    fun findByUserIdAndAuthority(
        userId: Long,
        authority: String,
    ): UserAuthorityEntity?

    // 권한 확인
    fun existsByUserIdAndAuthority(
        userId: Long,
        authority: String,
    ): Boolean

    fun hasAuthority(
        userId: Long,
        authority: String,
    ): Boolean

    // 사용자별 권한 관리
    fun deleteByUserId(userId: Long): Int

    fun deleteByUserIdAndAuthority(
        userId: Long,
        authority: String,
    ): Int

    // 페이지네이션 조회
    fun findByUserIdWithPagination(
        userId: Long,
        page: Int,
        size: Int,
    ): List<UserAuthorityEntity>

    fun findByAuthorityWithPagination(
        authority: String,
        page: Int,
        size: Int,
    ): List<UserAuthorityEntity>

    // 통계 및 집계
    fun countByUserId(userId: Long): Long

    fun countByAuthority(authority: String): Long
}
