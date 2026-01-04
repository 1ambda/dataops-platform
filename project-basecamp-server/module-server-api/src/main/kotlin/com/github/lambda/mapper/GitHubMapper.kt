package com.github.lambda.mapper

import com.github.lambda.domain.model.github.BranchComparison
import com.github.lambda.domain.model.github.CommitSummary
import com.github.lambda.domain.model.github.GitHubBranch
import com.github.lambda.domain.model.github.GitHubPullRequest
import com.github.lambda.domain.model.github.GitHubRepositoryEntity
import com.github.lambda.dto.github.BranchComparisonResponse
import com.github.lambda.dto.github.CommitSummaryResponse
import com.github.lambda.dto.github.GitHubBranchResponse
import com.github.lambda.dto.github.GitHubPullRequestListResponse
import com.github.lambda.dto.github.GitHubPullRequestResponse
import com.github.lambda.dto.github.GitHubRepositoryResponse
import com.github.lambda.dto.github.ListMetadata
import org.springframework.stereotype.Component

/**
 * GitHub Entity/Model to DTO Mapper
 */
@Component
class GitHubMapper {
    // ========================
    // Repository Mappings
    // ========================

    /**
     * Convert GitHubRepositoryEntity to GitHubRepositoryResponse
     */
    fun toResponse(entity: GitHubRepositoryEntity): GitHubRepositoryResponse =
        GitHubRepositoryResponse(
            id = entity.id ?: 0L,
            team = entity.team,
            owner = entity.owner,
            repoName = entity.repoName,
            repositoryUrl = entity.repositoryUrl,
            defaultBranch = entity.defaultBranch,
            developBranch = entity.developBranch,
            s3DevPath = entity.s3DevPath,
            s3ProdPath = entity.s3ProdPath,
            isActive = entity.isActive,
            description = entity.description,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    // ========================
    // Branch Mappings
    // ========================

    /**
     * Convert GitHubBranch to GitHubBranchResponse
     */
    fun toResponse(branch: GitHubBranch): GitHubBranchResponse =
        GitHubBranchResponse(
            name = branch.name,
            sha = branch.sha,
            isProtected = branch.isProtected,
            lastCommitDate = branch.lastCommitDate,
            lastCommitAuthor = branch.lastCommitAuthor,
            lastCommitMessage = branch.lastCommitMessage,
        )

    /**
     * Convert list of GitHubBranch to list of GitHubBranchResponse
     */
    fun toBranchResponseList(branches: List<GitHubBranch>): List<GitHubBranchResponse> = branches.map { toResponse(it) }

    /**
     * Convert BranchComparison to BranchComparisonResponse
     */
    fun toResponse(
        comparison: BranchComparison,
        baseBranch: String,
        headBranch: String,
    ): BranchComparisonResponse =
        BranchComparisonResponse(
            baseBranch = baseBranch,
            headBranch = headBranch,
            aheadBy = comparison.aheadBy,
            behindBy = comparison.behindBy,
            status = comparison.status.name,
            commits = comparison.commits.map { toResponse(it) },
        )

    /**
     * Convert CommitSummary to CommitSummaryResponse
     */
    fun toResponse(commit: CommitSummary): CommitSummaryResponse =
        CommitSummaryResponse(
            sha = commit.sha,
            message = commit.message,
            author = commit.author,
            date = commit.date,
        )

    // ========================
    // Pull Request Mappings
    // ========================

    /**
     * Convert GitHubPullRequest to GitHubPullRequestResponse
     */
    fun toResponse(pr: GitHubPullRequest): GitHubPullRequestResponse =
        GitHubPullRequestResponse(
            number = pr.number,
            title = pr.title,
            state = pr.state.name,
            sourceBranch = pr.sourceBranch,
            targetBranch = pr.targetBranch,
            author = pr.author,
            createdAt = pr.createdAt,
            updatedAt = pr.updatedAt,
            mergedAt = pr.mergedAt,
            mergedBy = pr.mergedBy,
            reviewers = pr.reviewers,
            labels = pr.labels,
            additions = pr.additions,
            deletions = pr.deletions,
            changedFiles = pr.changedFiles,
            url = pr.url,
        )

    /**
     * Convert list of GitHubPullRequest to GitHubPullRequestListResponse with metadata
     */
    fun toPullRequestListResponse(
        prs: List<GitHubPullRequest>,
        limit: Int,
    ): GitHubPullRequestListResponse =
        GitHubPullRequestListResponse(
            data = prs.map { toResponse(it) },
            metadata =
                ListMetadata(
                    total = prs.size,
                    limit = limit,
                    hasMore = prs.size >= limit,
                ),
        )
}
