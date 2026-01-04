package com.github.lambda.domain.external.github

import com.github.lambda.domain.model.github.PullRequestFilter

/**
 * GitHub API 클라이언트 인터페이스 (Domain Port)
 *
 * 외부 GitHub API와의 통신을 위한 도메인 포트 인터페이스입니다.
 * Branch, Pull Request 등의 정보를 조회하는 기능을 제공합니다.
 */
interface GitHubClient {
    // ========================
    // Repository Operations
    // ========================

    /**
     * Repository 유효성 검증
     *
     * @param owner GitHub owner (org or user)
     * @param repoName Repository name
     * @return Repository 존재 여부
     */
    fun validateRepository(
        owner: String,
        repoName: String,
    ): Boolean

    /**
     * Repository 정보 조회
     *
     * @param owner GitHub owner (org or user)
     * @param repoName Repository name
     * @return Repository 정보 또는 null (존재하지 않는 경우)
     */
    fun getRepositoryInfo(
        owner: String,
        repoName: String,
    ): GitHubRepositoryInfoResponse?

    // ========================
    // Branch Operations
    // ========================

    /**
     * Repository의 모든 Branch 목록 조회
     *
     * @param owner GitHub owner (org or user)
     * @param repoName Repository name
     * @return Branch 목록
     */
    fun listBranches(
        owner: String,
        repoName: String,
    ): List<GitHubBranchResponse>

    /**
     * 특정 Branch 정보 조회
     *
     * @param owner GitHub owner (org or user)
     * @param repoName Repository name
     * @param branchName Branch 이름
     * @return Branch 정보 또는 null (존재하지 않는 경우)
     */
    fun getBranch(
        owner: String,
        repoName: String,
        branchName: String,
    ): GitHubBranchResponse?

    /**
     * 두 Branch 비교
     *
     * @param owner GitHub owner (org or user)
     * @param repoName Repository name
     * @param baseBranch 기준 Branch (보통 main 또는 develop)
     * @param headBranch 비교 대상 Branch
     * @return Branch 비교 결과 또는 null (Branch가 존재하지 않는 경우)
     */
    fun compareBranches(
        owner: String,
        repoName: String,
        baseBranch: String,
        headBranch: String,
    ): BranchComparisonResponse?

    // ========================
    // Pull Request Operations
    // ========================

    /**
     * Repository의 Pull Request 목록 조회
     *
     * @param owner GitHub owner (org or user)
     * @param repoName Repository name
     * @param filter PR 필터 조건
     * @return Pull Request 목록
     */
    fun listPullRequests(
        owner: String,
        repoName: String,
        filter: PullRequestFilter = PullRequestFilter(),
    ): List<GitHubPullRequestResponse>

    /**
     * 특정 Pull Request 정보 조회
     *
     * @param owner GitHub owner (org or user)
     * @param repoName Repository name
     * @param prNumber PR 번호
     * @return Pull Request 정보 또는 null (존재하지 않는 경우)
     */
    fun getPullRequest(
        owner: String,
        repoName: String,
        prNumber: Long,
    ): GitHubPullRequestResponse?

    // ========================
    // Connection Check
    // ========================

    /**
     * GitHub API 연결 가능 여부 확인
     *
     * @return 연결 가능 여부
     */
    fun isAvailable(): Boolean
}
