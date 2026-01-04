package com.github.lambda.infra.repository.github

import com.github.lambda.domain.entity.github.GitHubRepositoryEntity
import com.github.lambda.domain.entity.github.QGitHubRepositoryEntity
import com.github.lambda.domain.repository.github.GitHubRepositoryDsl
import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

/**
 * GitHub Repository DSL 구현체
 *
 * QueryDSL을 사용하여 복잡한 쿼리 및 집계 작업을 구현합니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("gitHubRepositoryDsl")
class GitHubRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : GitHubRepositoryDsl {
    private val gitHubRepository = QGitHubRepositoryEntity.gitHubRepositoryEntity

    override fun findByRepositoryUrl(url: String): GitHubRepositoryEntity? =
        queryFactory
            .selectFrom(gitHubRepository)
            .where(
                gitHubRepository.repositoryUrl
                    .eq(url)
                    .and(gitHubRepository.deletedAt.isNull),
            ).fetchOne()

    override fun findByOwnerAndRepoName(
        owner: String,
        repoName: String,
    ): GitHubRepositoryEntity? =
        queryFactory
            .selectFrom(gitHubRepository)
            .where(
                gitHubRepository.owner
                    .eq(owner)
                    .and(gitHubRepository.repoName.eq(repoName))
                    .and(gitHubRepository.deletedAt.isNull),
            ).fetchOne()

    override fun searchByKeyword(keyword: String): List<GitHubRepositoryEntity> {
        val condition =
            BooleanBuilder()
                .and(gitHubRepository.deletedAt.isNull)
                .and(
                    gitHubRepository.team
                        .containsIgnoreCase(keyword)
                        .or(gitHubRepository.owner.containsIgnoreCase(keyword))
                        .or(gitHubRepository.repoName.containsIgnoreCase(keyword))
                        .or(gitHubRepository.description.containsIgnoreCase(keyword)),
                )

        return queryFactory
            .selectFrom(gitHubRepository)
            .where(condition)
            .orderBy(gitHubRepository.updatedAt.desc())
            .fetch()
    }

    override fun findByTeamIn(teams: List<String>): List<GitHubRepositoryEntity> =
        queryFactory
            .selectFrom(gitHubRepository)
            .where(
                gitHubRepository.team
                    .`in`(teams)
                    .and(gitHubRepository.deletedAt.isNull),
            ).orderBy(gitHubRepository.team.asc())
            .fetch()

    override fun findAllActive(): List<GitHubRepositoryEntity> =
        queryFactory
            .selectFrom(gitHubRepository)
            .where(
                gitHubRepository.isActive.isTrue
                    .and(gitHubRepository.deletedAt.isNull),
            ).orderBy(gitHubRepository.updatedAt.desc())
            .fetch()
}
