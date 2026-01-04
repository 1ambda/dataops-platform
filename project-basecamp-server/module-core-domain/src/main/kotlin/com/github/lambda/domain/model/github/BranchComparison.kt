package com.github.lambda.domain.model.github

import java.time.LocalDateTime

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
 * Branch 비교 상태
 */
enum class ComparisonStatus {
    /** head가 base보다 앞서 있음 (head가 더 최신 커밋 보유) */
    AHEAD,

    /** head가 base보다 뒤처져 있음 (base가 더 최신 커밋 보유) */
    BEHIND,

    /** head와 base가 서로 다른 커밋을 가지고 있음 */
    DIVERGED,

    /** head와 base가 동일한 커밋을 가리킴 */
    IDENTICAL,
}

/**
 * 커밋 요약 정보
 */
data class CommitSummary(
    val sha: String,
    val message: String,
    val author: String,
    val date: LocalDateTime,
)
