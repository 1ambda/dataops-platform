package com.github.lambda.domain.model.github

import java.time.LocalDateTime

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
