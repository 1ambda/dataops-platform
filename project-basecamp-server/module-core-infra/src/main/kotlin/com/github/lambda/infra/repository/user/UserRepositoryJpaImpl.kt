package com.github.lambda.infra.repository.user

import com.github.lambda.domain.entity.user.UserEntity
import com.github.lambda.domain.repository.user.UserRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

/**
 * 사용자 JPA Repository 구현 클래스
 *
 * Domain UserRepositoryJpa 인터페이스를 구현하고 Spring Data JPA를 직접 상속합니다.
 * UserRepositoryJpaSpringData와 UserRepositoryJpaImpl을 하나의 클래스로 통합했습니다.
 */
@Repository("userRepositoryJpa")
interface UserRepositoryJpaImpl :
    UserRepositoryJpa,
    JpaRepository<UserEntity, Long> {
    // Spring Data JPA 커스텀 쿼리 메서드들
    @Query("SELECT u FROM UserEntity u ORDER BY u.createdAt DESC")
    fun findAllOrderByCreatedAtDesc(pageable: Pageable): Page<UserEntity>

    @Query("SELECT u FROM UserEntity u WHERE u.enabled = :isActive ORDER BY u.createdAt DESC")
    fun findByIsActiveOrderByCreatedAtDesc(
        @Param("isActive") isActive: Boolean,
        pageable: Pageable,
    ): Page<UserEntity>

    fun countByEnabled(isActive: Boolean): Long

    // UserRepositoryJpa 인터페이스의 도메인 특화 메서드들을 구현

    // findById는 JpaRepository에서 제공되므로 별도 구현 불필요

    override fun findAllWithPagination(
        page: Int,
        size: Int,
    ): List<UserEntity> = findAllOrderByCreatedAtDesc(PageRequest.of(page, size)).content

    override fun findByActiveStatusWithPagination(
        isActive: Boolean,
        page: Int,
        size: Int,
    ): List<UserEntity> = findByIsActiveOrderByCreatedAtDesc(isActive, PageRequest.of(page, size)).content

    override fun countByActiveStatus(isActive: Boolean): Long = countByEnabled(isActive)

    override fun countAll(): Long = count()
}
