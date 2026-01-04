package com.github.lambda.domain.model.github

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
