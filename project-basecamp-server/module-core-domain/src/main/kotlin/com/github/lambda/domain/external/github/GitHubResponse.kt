package com.github.lambda.domain.external.github

import java.time.LocalDateTime

/**
 * GitHub Repository 정보 응답
 *
 * @param id Repository ID
 * @param name Repository 이름
 * @param fullName Full repository name (owner/repo)
 * @param description Repository 설명
 * @param defaultBranch 기본 Branch 이름
 * @param isPrivate Private repository 여부
 * @param language 주요 프로그래밍 언어
 * @param url GitHub URL
 * @param cloneUrl Git clone URL
 * @param sshUrl SSH clone URL
 * @param createdAt 생성 시간
 * @param updatedAt 마지막 업데이트 시간
 * @param stargazersCount Star 개수
 * @param forksCount Fork 개수
 */
data class GitHubRepositoryInfoResponse(
    val id: Long,
    val name: String,
    val fullName: String,
    val description: String?,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val language: String?,
    val url: String,
    val cloneUrl: String,
    val sshUrl: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val stargazersCount: Int,
    val forksCount: Int,
)

/**
 * GitHub Branch 정보 응답
 *
 * @param name Branch 이름
 * @param sha Commit SHA
 * @param isProtected Protected branch 여부
 * @param lastCommitDate 마지막 커밋 날짜
 * @param lastCommitAuthor 마지막 커밋 작성자
 * @param lastCommitMessage 마지막 커밋 메시지
 */
data class GitHubBranchResponse(
    val name: String,
    val sha: String,
    val isProtected: Boolean,
    val lastCommitDate: LocalDateTime?,
    val lastCommitAuthor: String?,
    val lastCommitMessage: String?,
)

/**
 * Branch 비교 결과 응답
 *
 * @param baseBranch 기준 Branch
 * @param headBranch 비교 대상 Branch
 * @param aheadBy Head가 Base보다 앞선 커밋 수
 * @param behindBy Head가 Base보다 뒤처진 커밋 수
 * @param status 비교 상태
 * @param totalCommits 총 커밋 수
 * @param filesChanged 변경된 파일 수
 * @param additions 추가된 줄 수
 * @param deletions 삭제된 줄 수
 */
data class BranchComparisonResponse(
    val baseBranch: String,
    val headBranch: String,
    val aheadBy: Int,
    val behindBy: Int,
    val status: String, // "identical", "ahead", "behind", "diverged"
    val totalCommits: Int,
    val filesChanged: Int,
    val additions: Int,
    val deletions: Int,
)

/**
 * GitHub Pull Request 정보 응답
 *
 * @param id PR ID
 * @param number PR 번호
 * @param title PR 제목
 * @param body PR 설명
 * @param state PR 상태
 * @param isDraft Draft PR 여부
 * @param baseBranch 기본 Branch
 * @param headBranch 비교 대상 Branch
 * @param author PR 작성자
 * @param assignees 담당자 목록
 * @param reviewers 리뷰어 목록
 * @param url PR URL
 * @param createdAt 생성 시간
 * @param updatedAt 마지막 업데이트 시간
 * @param mergedAt 머지 시간
 * @param closedAt 종료 시간
 * @param isMerged 머지 여부
 * @param isClosed 종료 여부
 * @param mergeCommitSha 머지 커밋 SHA
 * @param changedFiles 변경된 파일 수
 * @param additions 추가된 줄 수
 * @param deletions 삭제된 줄 수
 */
data class GitHubPullRequestResponse(
    val id: Long,
    val number: Long,
    val title: String,
    val body: String?,
    val state: String, // "open", "closed", "merged"
    val isDraft: Boolean,
    val baseBranch: String,
    val headBranch: String,
    val author: String,
    val assignees: List<String>,
    val reviewers: List<String>,
    val url: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val mergedAt: LocalDateTime?,
    val closedAt: LocalDateTime?,
    val isMerged: Boolean,
    val isClosed: Boolean,
    val mergeCommitSha: String?,
    val changedFiles: Int,
    val additions: Int,
    val deletions: Int,
)
