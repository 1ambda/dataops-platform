package com.github.lambda.infra.repository

import com.github.lambda.domain.entity.user.UserAuthorityEntity
import com.github.lambda.domain.repository.UserAuthorityRepositoryJpa
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
 * 사용자 권한 JPA Repository 구현 인터페이스
 *
 * Domain UserAuthorityRepositoryJpa 인터페이스를 구현하고 Spring Data JPA를 직접 상속합니다.
 * UserAuthorityRepositoryJpaSpringData와 UserAuthorityRepositoryJpaImpl을 하나의 인터페이스로 통합했습니다.
 */
@Repository("userAuthorityRepositoryJpa")
interface UserAuthorityRepositoryJpaImpl :
    UserAuthorityRepositoryJpa,
    JpaRepository<UserAuthorityEntity, Long> {
    // Spring Data JPA 커스텀 쿼리 메서드들 (기존 SpringData에서 이동)
    // 사용자별 권한 조회
    override fun findByUserId(userId: Long): List<UserAuthorityEntity>

    fun findByUserIdOrderByCreatedAtDesc(
        userId: Long,
        pageable: Pageable,
    ): Page<UserAuthorityEntity>

    // 권한별 조회
    override fun findByAuthority(authority: String): List<UserAuthorityEntity>

    fun findByAuthorityOrderByCreatedAtDesc(
        authority: String,
        pageable: Pageable,
    ): Page<UserAuthorityEntity>

    // 복합 조회
    override fun findByUserIdAndAuthority(
        userId: Long,
        authority: String,
    ): UserAuthorityEntity?

    // 권한 확인
    override fun existsByUserIdAndAuthority(
        userId: Long,
        authority: String,
    ): Boolean

    // 권한 삭제
    @Modifying
    @Query("DELETE FROM UserAuthorityEntity ua WHERE ua.userId = :userId")
    override fun deleteByUserId(
        @Param("userId") userId: Long,
    ): Int

    @Modifying
    @Query("DELETE FROM UserAuthorityEntity ua WHERE ua.userId = :userId AND ua.authority = :authority")
    override fun deleteByUserIdAndAuthority(
        @Param("userId") userId: Long,
        @Param("authority") authority: String,
    ): Int

    // 통계 및 집계
    override fun countByUserId(userId: Long): Long

    override fun countByAuthority(authority: String): Long

    // UserAuthorityRepositoryJpa 인터페이스의 도메인 특화 메서드들을 구현

    // findById는 JpaRepository에서 제공되므로 별도 구현 불필요

    override fun hasAuthority(
        userId: Long,
        authority: String,
    ): Boolean = existsByUserIdAndAuthority(userId, authority)

    override fun findByUserIdWithPagination(
        userId: Long,
        page: Int,
        size: Int,
    ): List<UserAuthorityEntity> = findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)).content

    override fun findByAuthorityWithPagination(
        authority: String,
        page: Int,
        size: Int,
    ): List<UserAuthorityEntity> = findByAuthorityOrderByCreatedAtDesc(authority, PageRequest.of(page, size)).content
}
