package com.dataops.basecamp.common.enums

/**
 * Pull Request 상태
 */
enum class PullRequestState {
    OPEN,
    CLOSED,
    MERGED,
}

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
