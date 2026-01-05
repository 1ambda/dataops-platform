package com.dataops.basecamp.domain.projection.github

import com.dataops.basecamp.common.enums.ComparisonStatus
import com.dataops.basecamp.common.enums.PullRequestState
import java.time.LocalDateTime

/**
 * GitHub Pull Request 정보 (External API로부터 조회)
 *
 * PR은 DB에 저장하지 않고 GitHub API를 통해 실시간으로 조회합니다.
 */
data class GitHubPullRequest(
    val number: Long,
    val title: String,
    val state: PullRequestState,
    val sourceBranch: String,
    val targetBranch: String,
    val author: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val mergedAt: LocalDateTime?,
    val mergedBy: String?,
    val reviewers: List<String>,
    val labels: List<String>,
    val additions: Int,
    val deletions: Int,
    val changedFiles: Int,
    val url: String,
)

/**
 * GitHub Branch 정보 (External API로부터 조회)
 *
 * Branch는 DB에 저장하지 않고 GitHub API를 통해 실시간으로 조회합니다.
 */
data class GitHubBranch(
    val name: String,
    val sha: String,
    val isProtected: Boolean,
    val lastCommitDate: LocalDateTime?,
    val lastCommitAuthor: String?,
    val lastCommitMessage: String?,
)

/**
 * Branch 비교 결과
 *
 * 두 브랜치 간의 커밋 차이를 비교한 결과입니다.
 */
data class BranchComparison(
    val aheadBy: Int,
    val behindBy: Int,
    val status: ComparisonStatus,
    val commits: List<CommitSummary>,
)

/**
 * 커밋 요약 정보
 */
data class CommitSummary(
    val sha: String,
    val message: String,
    val author: String,
    val date: LocalDateTime,
)

/**
 * GitHub Repository 정보 (External API로부터 조회)
 *
 * Repository의 기본 정보를 담는 데이터 클래스입니다.
 */
data class GitHubRepositoryInfo(
    val fullName: String,
    val description: String?,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val language: String?,
    val starCount: Int,
    val forkCount: Int,
)
