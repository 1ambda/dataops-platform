package com.github.lambda.domain.model.github

import com.github.lambda.common.enums.ComparisonStatus
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
 * 커밋 요약 정보
 */
data class CommitSummary(
    val sha: String,
    val message: String,
    val author: String,
    val date: LocalDateTime,
)
