package com.dataops.basecamp.domain.repository.github

import com.dataops.basecamp.domain.entity.github.GitHubRepositoryEntity

/**
 * GitHub Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * GitHub Repository에 대한 기본 CRUD 작업과 도메인 특화 쿼리 작업을 정의합니다.
 */
interface GitHubRepositoryJpa {
    // 기본 CRUD 작업
    fun save(repository: GitHubRepositoryEntity): GitHubRepositoryEntity

    fun findByIdOrNull(id: Long): GitHubRepositoryEntity?

    fun deleteById(id: Long)

    // 팀 기반 조회
    fun findByTeam(team: String): GitHubRepositoryEntity?

    fun existsByTeam(team: String): Boolean

    // URL 기반 조회
    fun existsByRepositoryUrl(url: String): Boolean

    // 활성 Repository 조회
    fun findByIsActiveTrue(): List<GitHubRepositoryEntity>

    fun findAllByOrderByUpdatedAtDesc(): List<GitHubRepositoryEntity>

    // 소유자 기반 조회
    fun findByOwner(owner: String): List<GitHubRepositoryEntity>
}
