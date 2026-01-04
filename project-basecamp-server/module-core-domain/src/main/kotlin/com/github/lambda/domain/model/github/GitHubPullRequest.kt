package com.github.lambda.domain.model.github

import com.github.lambda.common.enums.PullRequestState
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
 * Pull Request 상태
 */
enum class PullRequestState {
    OPEN,
    CLOSED,
    MERGED,
}

/**
 * Pull Request 필터 조건
 */
data class PullRequestFilter(
    val state: PullRequestState? = null,
    val author: String? = null,
    val targetBranch: String? = null,
    val limit: Int = 30,
)
