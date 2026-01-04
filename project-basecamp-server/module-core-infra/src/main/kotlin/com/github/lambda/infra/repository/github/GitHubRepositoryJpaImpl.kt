package com.github.lambda.infra.repository.github

import com.github.lambda.domain.entity.github.GitHubRepositoryEntity
import com.github.lambda.domain.repository.github.GitHubRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * GitHub Repository JPA 구현 인터페이스
 *
 * Domain GitHubRepositoryJpa 인터페이스와 JpaRepository를 모두 확장하는 Simplified Pattern입니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("gitHubRepositoryJpa")
interface GitHubRepositoryJpaImpl :
    GitHubRepositoryJpa,
    JpaRepository<GitHubRepositoryEntity, Long> {
    // 기본 조회 메서드들 (Spring Data JPA auto-implements)
    override fun findByTeam(team: String): GitHubRepositoryEntity?

    override fun existsByTeam(team: String): Boolean

    override fun existsByRepositoryUrl(url: String): Boolean

    override fun findByIsActiveTrue(): List<GitHubRepositoryEntity>

    override fun findAllByOrderByUpdatedAtDesc(): List<GitHubRepositoryEntity>

    override fun findByOwner(owner: String): List<GitHubRepositoryEntity>

    // Domain interface의 findById와 JpaRepository의 findById 충돌 해결
    override fun findByIdOrNull(id: Long): GitHubRepositoryEntity? = findById(id).orElse(null)
}
