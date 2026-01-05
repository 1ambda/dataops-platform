package com.dataops.basecamp.domain.repository.github

import com.dataops.basecamp.domain.entity.github.GitHubRepositoryEntity

/**
 * GitHub Repository DSL 인터페이스 (순수 도메인 추상화)
 *
 * GitHub Repository에 대한 복잡한 쿼리 및 집계 작업을 정의합니다.
 */
interface GitHubRepositoryDsl {
    /**
     * Repository URL로 조회
     */
    fun findByRepositoryUrl(url: String): GitHubRepositoryEntity?

    /**
     * Owner와 Repository 이름으로 조회
     */
    fun findByOwnerAndRepoName(
        owner: String,
        repoName: String,
    ): GitHubRepositoryEntity?

    /**
     * 키워드 검색 (team, owner, repoName, description)
     */
    fun searchByKeyword(keyword: String): List<GitHubRepositoryEntity>

    /**
     * 팀 목록으로 Repository 조회
     */
    fun findByTeamIn(teams: List<String>): List<GitHubRepositoryEntity>

    /**
     * 활성화된 Repository만 조회 (삭제되지 않고 is_active=true)
     */
    fun findAllActive(): List<GitHubRepositoryEntity>
}
